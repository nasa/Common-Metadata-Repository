(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require [clojure.string :as s]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.jobs :refer [defjob]]
            [cmr.common.cache :as cache]
            [cmr.transmit.index-set :as index-set]
            [cmr.common-app.services.search.query-model :as qm]
            [cmr.common-app.services.search.query-to-elastic :as q2e]
            ;; Required to be available at runtime.
            [cmr.search.data.query-to-elastic]
            [cmr.search.services.query-walkers.collection-concept-id-extractor :as cex]
            [cmr.search.services.query-walkers.provider-id-extractor :as pex]
            [cmr.common.services.errors :as e]
            [cmr.common.concepts :as concepts]
            [cmr.common-app.services.search.elastic-search-index :as common-esi]))

;; id of the index-set that CMR is using, hard code for now
(def index-set-id 1)

(defn- fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  [context]
  (let [fetched-index-set (index-set/get-index-set context index-set-id)
        rebalancing-collections (get-in fetched-index-set
                                        [:index-set :granule :rebalancing-collections])
        index-names-map (get-in fetched-index-set [:index-set :concepts])]
    ;; Remove any rebalancing collections from the set of granule index names we'll search through.
    {:index-names index-names-map
     :rebalancing-collections rebalancing-collections}))

(def index-cache-name
  "The name of the cache for caching index names. It will contain a map of concept type to a map of
   index names to the name of the index used in elasticsearch.

   Example:
   {:granule {:small_collections \"1_small_collections\"},
    :tag {:tags \"1_tags\"},
    :collection {:all-collection-revisions \"1_all_collection_revisions\",
                 :collections \"1_collections\"}}"
  :index-names)

(def index-names-cache-key
  "The key used for index names in the index cache."
  :concept-indices)

;; A job for refreshing the index names cache.
(defjob RefreshIndexNamesCacheJob
  [ctx system]
  (let [context {:system system}
        index-names (fetch-concept-type-index-names context)
        cache (cache/context->cache context index-cache-name)]
    (cache/set-value cache index-names-cache-key index-names)))

(def refresh-index-names-cache-job
  {:job-type RefreshIndexNamesCacheJob
   ;; 5 minutes
   :interval 300})

(defn- get-index-names-map
  "Fetch index names associated with concepts."
  [context]
  (cache/get-value (cache/context->cache context index-cache-name)
                   index-names-cache-key
                   (partial fetch-concept-type-index-names context)))

(defn- get-granule-index-names
  "Fetch index names associated with granules excluding rebalancing collections indexes"
  [context]
  (let [{:keys [index-names rebalancing-collections]} (get-index-names-map context)]
    (apply dissoc (:granule index-names) (map keyword rebalancing-collections))))

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

(defn all-granule-indexes
  "Returns all possible granule indexes in a string that can be used by elasticsearch query"
  [context]
  (let [{:keys [index-names rebalancing-collections]} (get-index-names-map context)
        granule-index-names (:granule index-names)
        rebalancing-indexes (map granule-index-names (map keyword rebalancing-collections))
        ;; Exclude all the rebalancing collection indexes.
        excluded-collections-str (if (seq rebalancing-indexes)
                                   (str "," (s/join "," (map #(str "-" %) rebalancing-indexes)))
                                   "")]
    (format "%d_c*,%d_small_collections,-%d_collections%s"
            index-set-id index-set-id index-set-id excluded-collections-str)))

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
      (all-granule-indexes context))))

(defmethod common-esi/concept-type->index-info :granule
  [context _ query]
  {:index-name (get-granule-indexes context query)
   :type-name "granule"})

(defmethod common-esi/concept-type->index-info :collection
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 "1_all_collection_revisions"
                 "collection_search_alias")
   :type-name "collection"})

(defmethod common-esi/concept-type->index-info :tag
  [context _ query]
  {:index-name "1_tags"
   :type-name "tag"})

(defn context->conn
  [context]
  (get-in context [:system :search-index :conn]))

(defn get-collection-permitted-groups
  "NOTE: Use for debugging only. Gets collections along with their currently permitted groups. This
  won't work if more than 10,000 collections exist in the CMR."
  [context]
  (let [index-info (common-esi/concept-type->index-info context :collection nil)
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
                                        {:terms {:field (q2e/query-field->elastic-field
                                                         :provider-id :granule)
                                                 :size 10000}
                                         :aggs {:by-collection-id
                                                {:terms {:field (q2e/query-field->elastic-field
                                                                  :collection-concept-seq-id
                                                                  :granule)
                                                         :size 10000}}}}}})
        results (common-esi/execute-query context query)
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
