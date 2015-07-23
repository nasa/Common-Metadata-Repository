(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.aggregation :as a]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojure.string :as s]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.cache :as cache]
            [cmr.elastic-utils.connect :as es]
            [cmr.transmit.index-set :as index-set]
            [cmr.search.models.results :as results]
            [cmr.search.models.query :as qm]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.services.query-walkers.collection-concept-id-extractor :as cex]
            [cmr.search.services.query-walkers.provider-id-extractor :as pex]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.common.services.errors :as e]
            [cmr.common.concepts :as concepts]))

;; id of the index-set that CMR is using, hard code for now
(def index-set-id 1)

(defn- fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  [context]
  (let [fetched-index-set (index-set/get-index-set context index-set-id)]
    (get-in fetched-index-set [:index-set :concepts])))

(def index-cache-name
  :index-names)

(defn- get-granule-index-names
  "Fetch index names associated with concepts."
  [context]
  (let [index-names (cache/get-value (cache/context->cache context index-cache-name)
                                     :concept-indices
                                     (partial fetch-concept-type-index-names context))]
    (get index-names :granule)))

(defn- collection-concept-id->index-name
  "Return the granule index name for the input collection concept id"
  [indexes coll-concept-id]
  (get indexes (keyword coll-concept-id) (get indexes :small_collections)))

(defn- collection-concept-ids->index-names
  "Return the granule index names for the input collection concept ids"
  [context coll-concept-ids]
  (let [indexes (get-granule-index-names context)]
    (distinct (map #(collection-concept-id->index-name indexes %) coll-concept-ids))))

(defn- provider-ids->index-names
  "Return the granule index names for the input provider-ids"
  [context provider-ids]
  (let [indexes (get-granule-index-names context)]
    (cons (get indexes :small_collections)
          (map #(format "%d_c*_%s" index-set-id (s/lower-case %))
               provider-ids))))

(def all-granule-indexes
  "Returns all possible granule indexes in a string that can be used by elasticsearch query"
  (format "%d_c*,%d_small_collections,-%d_collections" index-set-id index-set-id index-set-id))

(defn- get-granule-indexes
  "Returns the granule indexes that should be searched based on the input query"
  [context query]
  (let [coll-concept-ids (seq (cex/extract-collection-concept-ids query))
        provider-ids (seq (pex/extract-provider-ids query))]
    (cond
      coll-concept-ids
      ;; Use collection concept ids to limit the indexes queried
      (s/join "," (collection-concept-ids->index-names context coll-concept-ids))

      provider-ids
      ;; Use provider ids to limit the indexes queried
      (s/join "," (provider-ids->index-names context provider-ids))

      :else
      all-granule-indexes)))

(defn concept-type->index-info
  "Returns index info based on input concept type. For granule concept type, it will walks through
  the query and figures out only the relevant granule index names and return those."
  [context concept-type query]
  (if (= :collection concept-type)
    {:index-name  "1_collections"
     :type-name "collection"}
    {:index-name (get-granule-indexes context query)
     :type-name "granule"}))

(defmulti concept-type+result-format->fields
  "Returns the fields that should be selected out of elastic search given a concept type and result
  format"
  (fn [concept-type query]
    [concept-type (:result-format query)]))

(defrecord ElasticSearchIndex
  [
   config

   ;; The connection to elastic
   conn
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (assoc this :conn (es/try-connect (:config this))))

  (stop [this system]
        this))

(defn context->conn
  [context]
  (get-in context [:system :search-index :conn]))

(defmulti send-query-to-elastic
  "Created to trace only the sending of the query off to elastic search."
  (fn [context query]
    (:page-size query)))

(defmethod send-query-to-elastic :default
  [context query]
  (let [{:keys [page-size page-num concept-type result-format aggregations highlights]} query
        elastic-query (q2e/query->elastic query)
        sort-params (q2e/query->sort-params query)
        index-info (concept-type->index-info context concept-type query)
        fields (concept-type+result-format->fields concept-type query)
        from (* (dec page-num) page-size)
        query-map (util/remove-nil-keys {:query elastic-query
                                         :version true
                                         :sort sort-params
                                         :size page-size
                                         :from from
                                         :fields fields
                                         :aggs aggregations
                                         :highlight highlights})]
    (debug "Executing against indexes [" (:index-name index-info) "] the elastic query:"
           (pr-str elastic-query)
           "with sort" (pr-str sort-params)
           "with aggregations" (pr-str aggregations)
           "and highlights" (pr-str highlights))

      (esd/search (context->conn context)
                  (:index-name index-info)
                  [(:type-name index-info)]
                  query-map)))

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
    (e/internal-error! "Aggregations are not supported with queries with an unlimited page size."))

  (loop [page-num 1 prev-items [] took-total 0]
    (let [results (send-query-to-elastic
                    context (assoc query :page-num page-num :page-size unlimited-page-size))
          total-hits (get-in results [:hits :total])
          current-items (get-in results [:hits :hits])]

      (when (> total-hits max-unlimited-hits)
        (e/internal-error!
          (format "Query with unlimited page size matched %s items which exceeds maximum of %s. Query: %s"
                  total-hits max-unlimited-hits (pr-str query))))

      (if (>= (+ (count prev-items) (count current-items)) total-hits)
        ;; We've got enough results now. We'll return the query like we got all of them back in one request
        (-> results
            (update-in [:took] + took-total)
            (update-in [:hits :hits] concat prev-items))
        ;; We need to keep searching subsequent pages
        (recur (inc page-num) (concat prev-items current-items) (+ took-total (:took results)))))))

(defn get-collection-permitted-groups
  "NOTE: Use for debugging only. Gets collections along with their currently permitted groups. This
  won't work if more than 10,000 collections exist in the CMR."
  [context]
  (let [index-info (concept-type->index-info context :collection nil)
        results (esd/search (context->conn context)
                            (:index-name index-info)
                            [(:type-name index-info)]
                            :query (q/match-all)
                            :size 10000
                            :fields ["permitted-group-ids"])
        hits (get-in results [:hits :total])]
    (when (> hits (count (get-in results [:hits :hits])))
      (e/internal-error! "Failed to retrieve all hits."))
    (into {} (for [hit (get-in results [:hits :hits])]
               [(:_id hit) (get-in hit [:fields :permitted-group-ids])]))))

(defn execute-query
  "Executes a query to find concepts. Returns concept id, native id, and revision id."
  [context query]
  (let [start (System/currentTimeMillis)
        e-results (send-query-to-elastic context query)
        elapsed (- (System/currentTimeMillis) start)
        hits (get-in e-results [:hits :total])]
    (debug "Elastic query took" (:took e-results) "ms. Connection elapsed:" elapsed "ms")
    (when (and (= :unlimited (:page-size query)) (> hits (count (get-in e-results [:hits :hits])))
               (e/internal-error! "Failed to retrieve all hits.")))
    e-results))

(defn create-elastic-search-index
  "Creates a new instance of the elastic search index."
  [config]
  (->ElasticSearchIndex config nil))

(defn get-collection-granule-counts
  "Returns the collection granule count by searching elasticsearch by aggregation"
  [context provider-ids]
  (let [condition (if (seq provider-ids)
                    (qm/string-conditions :provider-id provider-ids true)
                    qm/match-all)
        query (qm/query {:concept-type :granule
                         :condition condition
                         :page-size 0
                         :aggregations {:by-provider
                                        {:terms {:field :provider-id
                                                 :size 10000}
                                         :aggs {:by-collection-id
                                                {:terms {:field :collection-concept-seq-id
                                                         :size 10000}}}}}})
        results (execute-query context query)
        extra-provider-count (get-in results [:aggregations :by-provider :sum_other_doc_count])]
    ;; It's possible that there are more providers with granules than we expected.
    ;; :sum_other_doc_count will be greater than 0 in that case.
    (when (> extra-provider-count 0)
      (e/internal-error! "There were [%s] more providers with granules than we ever expected to see."))


    (into {} (for [provider-bucket (get-in results [:aggregations :by-provider :buckets])
                   :let [extra-collection-count (get-in provider-bucket [:by-collection-id :sum_other_doc_count])]
                   coll-bucket (get-in provider-bucket [:by-collection-id :buckets])
                   :let [provider-id (:key provider-bucket)
                         coll-seq-id (:key coll-bucket)
                         num-granules (:doc_count coll-bucket)]]
               (do
                 ;; It's possible that there are more collections in the provider than we expected.
                 ;; :sum_other_doc_count will be greater than 0 in that case.
                 (when (> extra-collection-count 0)
                   (e/internal-error!
                     (format "Provider %s has more collections ([%s]) with granules than we support"
                             provider-id extra-collection-count)))

                 [(concepts/build-concept-id {:sequence-number coll-seq-id
                                              :provider-id provider-id
                                              :concept-type :collection})
                  num-granules])))))





