(ns cmr.common-app.services.search.elastic-search-index
  "Implements searching against Elasticsearch. Defines an Elastic Search Index component."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojurewerkz.elastisch.aggregation :as a]
   [clojurewerkz.elastisch.rest.document :as esd]
   [clojurewerkz.elastisch.rest.index :as esri]
   [clojurewerkz.elastisch.rest.response :as esrsp]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common-app.services.search.results-model :as results]
   [cmr.common.concepts :as concepts]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.connect :as es]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.transmit.connection :as transmit-conn])
  (:import
   clojure.lang.ExceptionInfo
   java.net.UnknownHostException))

(defmulti concept-type->index-info
  "Returns index info based on input concept type. The map should contain a :type-name key along with
   an :index-name key. :index-name can refer to a single index or a comma separated string of multiple
   index names."
  (fn [context concept-type query]
    concept-type))

(defmulti concept-type+result-format->fields
  "Returns the fields that should be selected out of elastic search given a concept type and result
  format"
  (fn [concept-type query]
    [concept-type (qm/base-result-format query)]))

(defn context->search-index
  "Returns the search index given a context. This assumes that the search index is always located in a
   system using the :search-index key."
  [context]
  (get-in context [:system :search-index]))

(defn context->conn
  "Returns the connection given a context. This assumes that the search index is always located in a
   system using the :search-index key."
  [context]
  (:conn (context->search-index context)))

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
  (let [{:keys [page-size offset concept-type aggregations highlights scroll scroll-id search-after]} query
        scroll-timeout (when scroll (es-config/elastic-scroll-timeout))
        search-type (if scroll
                        (es-config/elastic-scroll-search-type)
                        "query_then_fetch")
        sort-params (q2e/query->sort-params query)
        fields (query-fields->elastic-fields
                 concept-type
                 (or (:result-fields query) (concept-type+result-format->fields concept-type query)))]
    {:version true
     :timeout (es-config/elastic-query-timeout)
     :sort sort-params
     :size page-size
     :from offset
     :_source fields
     :aggs aggregations
     :scroll scroll-timeout
     :scroll-id scroll-id
     :search-after search-after
     :search_type search-type
     :highlight highlights}))

(defmulti handle-es-exception
  "Handles exceptions from ES. Unexpected exceptions are simply re-thrown."
  (fn [ex scroll-id]
    (:status (ex-data ex))))

(defmethod handle-es-exception 404
  [ex scroll-id]
  (if scroll-id
    (errors/throw-service-error :not-found (format "Scroll session [%s] does not exist" scroll-id))
    (throw ex)))

(defmethod handle-es-exception :default
  [ex _]
  (throw ex))

(defn- scroll-search
  "Performs a scroll search, handling errors where possible."
  [context scroll-id]
  (try
    (es-helper/scroll (context->conn context) scroll-id {:scroll (es-config/elastic-scroll-timeout)})
    (catch ExceptionInfo e
      (handle-es-exception e scroll-id))))

(defn- do-send-with-retry
  "Sends a query to ES, either normal or using a scroll query."
  [context index-info query max-retries]
  (try
    (if (> max-retries 0)
      (if-let [scroll-id (:scroll-id query)]
        (scroll-search context scroll-id)
        (es-helper/search
         (context->conn context) (:index-name index-info) [(:type-name index-info)] query))
      (errors/throw-service-error :service-unavailable "Exhausted retries to execute ES query"))

    (catch UnknownHostException e
      (info (format
             (str "Execute ES query failed due to UnknownHostException. Retry in %.3f seconds."
                  " Will retry up to %d times.")
             (/ (es-config/elastic-unknown-host-retry-interval-ms) 1000.0)
             max-retries))
      (Thread/sleep (es-config/elastic-unknown-host-retry-interval-ms))
      (do-send-with-retry context index-info query (- max-retries 1)))

    (catch clojure.lang.ExceptionInfo e
      (when-let [body (:body (ex-data e))]
        (when (re-find #"Trying to create too many scroll contexts" body)
          (info "Execute ES query failed due to" body)
          (errors/throw-service-error
           :too-many-requests
           "CMR is currently experiencing too many scroll searches to complete this request.
           Scroll is deprecated in CMR. Please consider switching your scroll requests to use search-after.
           See https://cmr.earthdata.nasa.gov/search/site/docs/search/api.html#search-after.
           This will make your workflow simpler (no more clear-scroll calls) and improve the stability of both your searches and CMR.
           Thank you!"))

        (when (re-find #"Trying to create too many buckets" body)
          (info "Execute ES query failed due to" body)
          (errors/throw-service-error
           :payload-too-large
           "The search is creating more buckets than allowed by CMR. Please narrow your search."))

        (when (re-find #"maxClauseCount is set to 1024" body)
          (info "Execute ES query failed due to" body)
          (errors/throw-service-error
           :payload-too-large
           "The search is creating more clauses than allowed by CMR. Please narrow your search."))
        
        (when (and (or (re-find #"\"type\":\"illegal_argument_exception\"" body)
                       (re-find #"\"type\":\"parsing_exception\"" body))
                   (re-find #"search_after" body))
          (info "Execute ES query failed due to" body)
          (let [err-msg (-> (json/parse-string body true)
                            (get-in [:error :root_cause])
                            str)]
            (errors/throw-service-error
             :bad-request
             (format "The search failed with error: %s. Please double check your search_after header." err-msg)))))
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
  (fn [context query]
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
    (info "Executing against indexes [" (:index-name index-info) "] the elastic query:"
           (pr-str elastic-query)
           "with sort" (pr-str sort-params)
           "with aggregations" (pr-str aggregations)
           "and highlights" (pr-str highlights))
    (when-let [scroll-id (:scroll-id query-map)]
      (info "Using scroll-id" scroll-id))
    (when-let [search-after (:search_after query-map)]
      (info "Using search-after" search-after))
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

(def unlimited-page-size
  "This is the number of items we will request at a time when the page size is set to unlimited"
  10000)

(def max-unlimited-hits
  "This is the maximum number of hits we can fetch if the page size is set to unlimited. We need to
  support fetching fields on every single collection in the CMR. This is set to a number safely above
  what we'll need to support with GCMD (~35K) and ECHO (~5k) collections."
  100000)

;; Implements querying against elasticsearch when the page size is set to :unlimited. It works by
;; calling the default implementation multiple times until all results have been found. It uses
;; the constants defined above to control how many are requested at a time and the maximum number
;; of hits that can be retrieved.
(defmethod send-query-to-elastic :unlimited
  [context query]
  (when (:aggregations query)
    (errors/internal-error! "Aggregations are not supported with queries with an unlimited page size."))

  (loop [offset 0 prev-items [] took-total 0 timed-out false]
    (let [results (send-query-to-elastic
                    context (assoc query :offset offset :page-size unlimited-page-size))
          total-hits (get-in results [:hits :total :value])
          current-items (get-in results [:hits :hits])]

      (when (> total-hits max-unlimited-hits)
        (errors/internal-error!
          (format "Query with unlimited page size matched %s items which exceeds maximum of %s. Query: %s"
                  total-hits max-unlimited-hits (pr-str query))))

      (if (>= (+ (count prev-items) (count current-items)) total-hits)
        ;; We've got enough results now. We'll return the query like we got all of them back in one request
        (-> results
            (update-in [:took] + took-total)
            (update-in [:hits :hits] concat prev-items)
            (assoc :timed_out timed-out))
        ;; We need to keep searching subsequent pages
        (recur (long (+ offset unlimited-page-size))
               (concat prev-items current-items)
               (long (+ took-total (:took results)))
               (or timed-out (:timed_out results)))))))


(defn execute-query
  "Executes a query to find concepts. Returns concept id, native id, and revision id."
  [context query]
  (let [start (System/currentTimeMillis)
        e-results (send-query-to-elastic context query)
        elapsed (- (System/currentTimeMillis) start)
        hits (get-in e-results [:hits :total :value])]
    (info "Elastic query took" (:took e-results) "ms. Connection elapsed:" elapsed "ms")
    (when (and (= :unlimited (:page-size query)) (> hits (count (get-in e-results [:hits :hits]))))
      (errors/internal-error! "Failed to retrieve all hits."))
    e-results))

(defn refresh
  "Make changes written to Elasticsearch available for search. See
   https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-refresh.html"
  [context]
  (esri/refresh (context->conn context)))

(defrecord ElasticSearchIndex
  [
   config

   ;; The connection to elastic
   conn]


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (assoc this :conn (es/try-connect (:config this))))

  (stop [this system]
        this))

(defn create-elastic-search-index
  "Creates a new instance of the elastic search index."
  []
  (->ElasticSearchIndex (es-config/elastic-config) nil))
