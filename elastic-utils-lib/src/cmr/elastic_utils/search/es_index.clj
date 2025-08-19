(ns cmr.elastic-utils.search.es-index
  "Implements searching against Elasticsearch. Defines an Elastic Search Index component."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojurewerkz.elastisch.rest.index :as esri]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [debug info warnf]]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.search.query-model :as qm]
   [cmr.common.util :as util]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.connect :as es]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.search.es-query-to-elastic :as q2e]
   [cmr.transmit.connection :as transmit-conn])
  (:import
   clojure.lang.ExceptionInfo
   java.net.UnknownHostException))

(defmulti concept-type->index-info
  "Returns index info based on input concept type. The map should contain a :type-name key along
   with an :index-name key. :index-name can refer to a single index or a comma separated string of
   multiple index names.
   All index name strings in index-name are expected to be the same type as :type-name."
  (fn [_context concept-type _query]
    concept-type))

;; TODO 10636 -- why are these defmethods here and in search app and duplicated in access-control?...need to consolidate this
(defmethod concept-type->index-info :collection
  [_context _ query]
  {:index-name (if (:all-revisions? query)
                 "1_all_collection_revisions"
                 (es-config/collections-index-alias))
   :type-name "collection"})

(defmulti concept-type+result-format->fields
  "Returns the fields that should be selected out of elastic search given a concept type and result
   format"
  (fn [concept-type query]
    [concept-type (qm/base-result-format query)]))

(defmethod concept-type+result-format->fields [:granule :xml]
  [_concept-type _query]
  ["granule-ur"
   "provider-id"
   "concept-id"])

(defmethod concept-type+result-format->fields [:collection :xml]
  [_concept-type _query]
  ["entry-title"
   "provider-id"
   "short-name"
   "version-id"
   "concept-id"
   "deleted"
   "revision-id"
   "_score"])

(defn context->search-index
  "Returns the search index given a context. This assumes that the search index is always located
   in a system using the :search-index key."
  [context es-cluster-name]
  (cond
    (= es-cluster-name cmr.elastic-utils.config/non-gran-elastic-name) (get-in context [:system :non-gran-search-index])
    (= es-cluster-name cmr.elastic-utils.config/gran-elastic-name) (get-in context [:system :gran-search-index])
    :else (throw (Exception. (str "expected specific elastic name but got " es-cluster-name " instead.")))))

(defn context->conn
  "Returns the connection given a context. This assumes that the search index is always located in
   a system using the :search-index key."
  [context es-cluster-name]
  (:conn (context->search-index context es-cluster-name)))

(defn- ensure-sort-keys-for-search-after
  "Ensures sort-keys include _id for search_after"
  [sort-keys]
  (if (some #(= (:field %) "_id") sort-keys)
    sort-keys
    (conj (vec sort-keys) {:field "_id" :order :asc})))

(defn- extract-search-after-values
  "Extracts the sort values from the last hit for use in search_after"
  [hits]
  (when-let [last-hit (last hits)]
    (:sort last-hit)))

(defn- query-fields->elastic-fields
  "Converts all of the CMR business logic field names to the actual fields in elastic."
  [concept-type fields]
  (map #(q2e/query-field->elastic-field (keyword %) concept-type) fields))

(defn- query->execution-params
  "Returns the Elasticsearch execution parameters extracted from the query. These are the
  actual ES parameters as expected by the elastisch library. The :scroll-id parameter is special
  and is stripped out before calling elastisch to determine whether a normal search call or a
  scroll call should be made."
  [query]
  (let [{:keys [page-size
                offset
                concept-type
                aggregations
                highlights
                scroll
                scroll-id
                search-after
                remove-source]} query
        sort-params (q2e/query->sort-params query)
        scroll-timeout (when scroll (es-config/elastic-scroll-timeout))
        search-type (if scroll
                      (es-config/elastic-scroll-search-type)
                      "query_then_fetch")
        fields (query-fields->elastic-fields
                concept-type
                (or (:result-fields query)
                    (concept-type+result-format->fields concept-type query)))]
    {:version true
     :timeout (es-config/elastic-query-timeout)
     :sort sort-params
     :size page-size
     :from offset
     :_source (if (nil? remove-source) fields false)
     :aggs aggregations
     :scroll scroll-timeout
     :scroll-id scroll-id
     :search-after search-after
     :search_type search-type
     :highlight highlights}))

(defmulti handle-es-exception
  "Handles exceptions from ES. Unexpected exceptions are simply re-thrown."
  (fn [ex _scroll-id]
    (:status (ex-data ex))))

(defmethod handle-es-exception 404
  [ex scroll-id]
  (if scroll-id
    (errors/throw-service-error :not-found (format "Scroll session [%s] does not exist" scroll-id))
    (throw ex)))

(defmethod handle-es-exception :default
  [ex _scroll-id]
  (throw ex))

;; TODO 10636 Fix
(defn- scroll-search
  "Performs a scroll search, handling errors where possible."
  [context scroll-id]
  (try
    (es-helper/scroll
     (context->conn context cmr.elastic-utils.config/gran-elastic-name)
     scroll-id
     {:scroll (es-config/elastic-scroll-timeout)})
    (catch ExceptionInfo e
      (handle-es-exception e scroll-id))))

;; TODO 10636 FIX THIS
;; TODO 10636 this is hardcoded to index name...could it be better? Will these rules always be true?
;; TODO unit test this --  need a sys test as well, so that if any index is created or found, we will auto warn that something could break with this
(defn get-es-cluster-name-from-index-name
  "Returns the Elasticsearch cluster name based on the index name."
  [index-name]
  ;; NOTE: expecting index-name to represent only one index-name as a string
  ;(println "10636- INSIDE get-es-cluster-from-index-name. Given index-name = " index-name)
  (let [gran-cluster cmr.elastic-utils.config/gran-elastic-name
        non-gran-cluster cmr.elastic-utils.config/non-gran-elastic-name
        gran-index-set-name (str gran-cluster "-index-sets")

        excluded-indices #{"collection_search_alias" "1_collections_v2"}
        gran-specific-indices #{"1_small_collections" "1_deleted_granules"}

        uses-gran-cluster? (and
                            (not (excluded-indices index-name))
                            (or (string/starts-with? index-name "1_c")
                                (gran-specific-indices index-name)
                                (= index-name gran-index-set-name)))]
    (if uses-gran-cluster?
      gran-cluster
      non-gran-cluster)))

(defn get-es-cluster-name-by-index-info-type-name
  [index-info]
  (if (= (:type-name index-info) "granule")
    cmr.elastic-utils.config/gran-elastic-name
    cmr.elastic-utils.config/non-gran-elastic-name))

(defn- do-send-with-retry
  "Sends a query to ES, either normal or using a scroll query."
  [context index-info query max-retries]
  ;; example index-info is {:index-name collection_search_alias, :type-name collection} OR {:index-name 1_c*,1_small_collections,-1_collections*, :type-name granule}
  ;; will index-info always be one element or an array?
  ;(println "INSIDE do-send-with-retry with index info = " index-info " and query = " query)
  ;; index info =  {:index-name , :type-name granule}
  ;; query =  {:search_type query_then_fetch, :size 10, :from 0, :timeout 170s, :version true, :query {:bool {:must {:match_all {}}, :filter {:bool {:must ({:term {:collection-concept-id-doc-values C1200000001-JM_PROV1}} {:term {:concept-id G1200000002-JM_PROV1}})}}}}, :_source (:concept-id :revision-id :native-id-stored :provider-id-doc-values :metadata-format :revision-date-stored-doc-values :collection-concept-id-doc-values), :sort ({:provider-id-lowercase-doc-values {:order :asc}} {:start-date-doc-values {:order :asc}} {:concept-seq-id-long {:order asc}})}
  (println "10636- INSIDE do-send-with-retry with index-info = " index-info ". Determined the es cluster is = " (get-es-cluster-name-by-index-info-type-name index-info))
  (try
    (if (pos? max-retries)
      (if-let [scroll-id (:scroll-id query)]
        (scroll-search context scroll-id)
        (es-helper/search
          (context->conn context (get-es-cluster-name-by-index-info-type-name index-info))
          (:index-name index-info)
          [(:type-name index-info)]
          query))
      (errors/throw-service-error :service-unavailable "Exhausted retries to execute ES query"))

    (catch UnknownHostException _e
      (warnf (str "Execute ES query failed because of UnknownHostException. Retry in %.3f seconds. "
                  "Will retry up to %d times. No more then %d tries can be made.")
             (/ (es-config/elastic-unknown-host-retry-interval-ms) 1000.0)
             max-retries
             (es-config/elastic-unknown-host-retries))
      (Thread/sleep (es-config/elastic-unknown-host-retry-interval-ms))
      (do-send-with-retry context index-info query (dec max-retries)))

    (catch clojure.lang.ExceptionInfo e
      (when-let [body (:body (ex-data e))]

        (warnf "Execute ES query in do-send-with-retry failed with message '%s' and body «%s»"
               (.getMessage e)
               (util/trunc body 1024))

        (when (re-find #"Trying to create too many scroll contexts" body)
          (errors/throw-service-error
           :too-many-requests
           "CMR is currently experiencing too many scroll searches to complete this request.
            Scroll is deprecated in CMR. Please consider switching your scroll requests to use
            search-after. See
            https://cmr.earthdata.nasa.gov/search/site/docs/search/api.html#search-after.
            This will make your workflow simpler (no more clear-scroll calls) and improve the
            stability of both your searches and CMR. Thank you!"))

        (when (re-find #"Trying to create too many buckets" body)
          (errors/throw-service-error
           :payload-too-large
           "The search is creating more buckets than allowed by CMR. Please narrow your search."))

        (when (re-find #"maxClauseCount is set to 1024" body)
          (errors/throw-service-error
           :payload-too-large
           "The search is creating more clauses than allowed by CMR. Please narrow your search."))

        (when (re-find #"Cannot normalize a vector length 0" body)
          (errors/throw-service-error
           :bad-request
           "The search shapefile points are too close together and have too much precision. Please
            reduce precision or simplify your shapefile."))

        (when (and (or (re-find #"\"type\":\"illegal_argument_exception\"" body)
                       (re-find #"\"type\":\"parsing_exception\"" body))
                   (re-find #"search_after" body))
          (let [err-msg (-> (json/parse-string body true)
                            (get-in [:error :root_cause])
                            str)]
            (errors/throw-service-error
             :bad-request
             (format
              "The search failed with error: %s. Please double check your search_after header."
              err-msg))))

        (throw (ex-info "An unhandled exception occurred" {} e)))
      ;; for other errors, rethrow the exception
      (throw e))))

(defn- do-send
  "Sends a query to ES, either normal or using a scroll query."
  [context index-info query]
  (do-send-with-retry context index-info query (es-config/elastic-unknown-host-retries)))

(defn- send-query
  "Send the query to ES using either a normal query or a scroll query. Handle socket exceptions
  by retrying."
  [context index-info query]
  (transmit-conn/handle-socket-exception-retries
   (do-send context index-info query)))

(defmulti send-query-to-elastic
  "Created to trace only the sending of the query off to elastic search."
  (fn [_context query]
    (:page-size query)))

(defmethod send-query-to-elastic :default
  [context query]
  (let [elastic-query (q2e/query->elastic query)
        {sort-params :sort
         aggregations :aggs
         highlights :highlight :as execution-params} (query->execution-params query)
        concept-type (:concept-type query)
        index-info (concept-type->index-info context concept-type query)
        query-map (-> elastic-query
                      (merge execution-params)
                      ;; rename search-after to search_after for ES execution
                      (set/rename-keys {:search-after :search_after})
                      util/remove-nil-keys)]
    (debug "Executing against indexes [" (:index-name index-info) "] the elastic query:"
           (pr-str elastic-query)
           "with sort" (pr-str sort-params)
           "with aggregations" (pr-str aggregations)
           "and highlights" (pr-str highlights))
    (when-let [scroll-id (:scroll-id query-map)]
      (debug "Using scroll-id" scroll-id))
    (when-let [search-after (:search_after query-map)]
      (debug "Using search-after" (pr-str search-after)))
    (let [response (send-query context index-info query-map)]
      ;; Replace the Elasticsearch field names with their query model field names within the results
      (update-in response [:hits :hits]
                 (fn [all-concepts]
                   (map (fn [single-concept-result]
                          (update-in single-concept-result [:_source]
                                     (fn [field]
                                       (set/rename-keys field
                                                        (q2e/elastic-field->query-field-mappings
                                                         concept-type)))))
                        all-concepts))))))

(defmethod send-query-to-elastic :unlimited
  [context query]
  (when (:aggregations query)
    (errors/internal-error!
     "Aggregations are not supported with queries with an unlimited page size."))

  (debug "Executing unlimited page size query with query:"
         (pr-str query))

  ;; Use search_after for efficient deep pagination
  (let [query-with-sort (update query :sort-keys ensure-sort-keys-for-search-after)
        batch-size (es-config/es-unlimited-page-size)
        ;; First request to get total hits
        first-response (send-query-to-elastic
                        context
                        (assoc query-with-sort
                               :page-size batch-size
                               :search-after nil))
        total-hits (get-in first-response [:hits :total :value])]

    ;; Check if we're within the safety limit
    (when (> total-hits (es-config/es-max-unlimited-hits))
      (errors/internal-error!
       (format
        "Query with unlimited page size matched %s items which exceeds maximum of %s. Query: %s"
        total-hits (es-config/es-max-unlimited-hits) (pr-str query))))

    ;; If we got all results in the first batch, return immediately
    (if (<= total-hits batch-size)
      first-response
      ;; Otherwise, use search_after to get all results
      (loop [accumulated-hits (get-in first-response [:hits :hits])
             search-after-values (extract-search-after-values accumulated-hits)
             took-total (:took first-response)
             timed-out (:timed_out first-response)]
        (debug "Accumulated hits so far:" (count accumulated-hits)
               "with total hits:" total-hits
               "and took time:" took-total
               "timed out:" timed-out)
        (if (or (nil? search-after-values)
                (>= (count accumulated-hits) total-hits))
          ;; We've got all results
          (do
            (debug "Returning all results with total hits:" total-hits
                   "and took time:" took-total
                   "timed out:" timed-out)
            ;; Return the first response with the accumulated hits and total took time)
            (-> first-response
                (assoc :took took-total)
                (assoc :timed_out timed-out)
                (assoc-in [:hits :hits] accumulated-hits)))

          (let [next-response (send-query-to-elastic
                               context
                               (assoc query-with-sort
                                      :page-size batch-size
                                      :search-after search-after-values))
                new-hits (get-in next-response [:hits :hits])]
            (debug "Received next batch of hits with size:" (count new-hits)
                   "and took time:" (:took next-response)
                   "timed out:" (:timed_out next-response))
            (recur (concat accumulated-hits new-hits)
                   (extract-search-after-values new-hits)
                   (+ took-total (:took next-response))
                   (or timed-out (:timed_out next-response)))))))))

(defn execute-query
  "Executes a query to find concepts. Returns concept id, native id, and revision id."
  [context query]
  (let [start (System/currentTimeMillis)
        e-results (send-query-to-elastic context query)
        elapsed (- (System/currentTimeMillis) start)
        hits (get-in e-results [:hits :total :value])]
    (info "Elastic query for concept-type:" (:concept-type query) " and result format: "
          (:result-format query) "took" (:took e-results) "ms. Connection elapsed:" elapsed "ms")
    (when (and (= :unlimited (:page-size query)) (> hits (count (get-in e-results [:hits :hits]))))
      (errors/internal-error! "Failed to retrieve all hits."))
    e-results))

(defn refresh
  "Make changes written to Elasticsearch available for search. See
   https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-refresh.html"
  [context]
  (esri/refresh (context->conn context cmr.elastic-utils.config/non-gran-elastic-name))
  (esri/refresh (context->conn context cmr.elastic-utils.config/gran-elastic-name)))

(defrecord ElasticSearchIndex
           ;; conn is the connection to elastic
           [config conn]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this _system]
    (assoc this :conn (es/try-connect (:config this))))

  (stop [this _system] this))

(defn create-elastic-search-index
  "Creates a new instance of the elastic search index."
  [es-cluster-name]
  (cond
    (= es-cluster-name es-config/non-gran-elastic-name) (->ElasticSearchIndex (es-config/non-gran-elastic-config) nil)
    (= es-cluster-name es-config/gran-elastic-name) (->ElasticSearchIndex (es-config/gran-elastic-config) nil)
    :else (throw (Exception. (str "expected valid elastic name but got " es-cluster-name " instead.")))))
