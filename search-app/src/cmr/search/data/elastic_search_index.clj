(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.hash-cache :as hcache]
   [cmr.common.util :as util]
   [cmr.common.services.errors :as e]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-index :as common-esi]
   [cmr.elastic-utils.search.es-index-name-cache :as index-names-cache]
   [cmr.elastic-utils.search.es-query-to-elastic :as q2e]
   [cmr.elastic-utils.search.es-wrapper :as q]
   [cmr.search.services.query-walkers.collection-concept-id-extractor :as cex]
   [cmr.search.services.query-walkers.provider-id-extractor :as pex])
  ;; Required to be available at runtime.
  (:require
   [cmr.search.data.elastic-relevancy-scoring]
   [cmr.search.data.query-to-elastic]))

;; id of the index-set that CMR is using, hard code for now
(def index-set-id 1)

(declare collections-index-alias)
(defconfig collections-index-alias
  "The alias to use for the collections index."
  {:default "collection_search_alias" :type String})

;; Simplifies the cache key used in the funtions below.
(def cache-key index-names-cache/index-names-cache-key)

(defn- get-granule-index-names
  "Fetch index names associated with granules excluding rebalancing collections indexes"
  [context]
  (let [cache (hcache/context->cache context cache-key)
        granule-index-names (or (hcache/get-value cache cache-key :granule)
                                (do
                                  (index-names-cache/refresh-index-names-cache context)
                                  (hcache/get-value cache cache-key :granule)))
        rebalancing-collections (hcache/get-value cache cache-key :rebalancing-collections)]
    (apply dissoc granule-index-names (map keyword rebalancing-collections))))
(comment 
  (println indexes)
  )
(defn- collection-concept-id->index-name
  "Return the granule index name for the input collection concept id"
  [indexes coll-concept-id]
  (def indexes indexes)
  (def coll-concept-id coll-concept-id)
  (get indexes (keyword coll-concept-id) (if (cfg/provider-granules)
                                           (get indexes (keyword (str "granules_" (util/safe-lowercase
                                                                                   (concepts/concept-id->provider-id coll-concept-id)))))
                                           (get indexes :small_collections))))

(defn- collection-concept-ids->index-names
  "Return the granule index names for the input collection concept ids"
  [context coll-concept-ids]
  (let [indexes (get-granule-index-names context)]
    (distinct (map #(collection-concept-id->index-name indexes %) coll-concept-ids))))

(comment
  (println context)
  )
(defn- provider-ids->index-names
  "Return the granule index names for the input provider-ids"
  [context provider-ids]
  (def context context)
  (def provider-ids provider-ids)
  (let [indexes (get-granule-index-names context)]
    (if (cfg/provider-granules)
      (concat (map #(format "%d_granules_%s" index-set-id (string/lower-case %))
                   provider-ids)
              (map #(format "%d_c*_%s" index-set-id (string/lower-case %))
                   provider-ids))
      (cons (get indexes :small_collections)
            (map #(format "%d_c*_%s" index-set-id (string/lower-case %))
                 provider-ids)))))

(defn all-granule-indexes
  "Returns all possible granule indexes in a string that can be used by elasticsearch query"
  [context]
  (let [cache (hcache/context->cache context cache-key)
        granule-index-names (or (hcache/get-value cache cache-key :granule)
                                (do
                                  (index-names-cache/refresh-index-names-cache context)
                                  (hcache/get-value cache cache-key :granule)))
        rebalancing-collections (hcache/get-value cache cache-key :rebalancing-collections)
        rebalancing-indexes (map granule-index-names (map keyword rebalancing-collections))
        ;; Exclude all the rebalancing collection indexes.
        excluded-collections-str (if (seq rebalancing-indexes)
                                   (str "," (string/join "," (map #(str "-" %) rebalancing-indexes)))
                                   "")]
    (if (cfg/provider-granules)
      (format "%d_c*,%d_granules_*,-%d_collections*%s"
              index-set-id index-set-id index-set-id excluded-collections-str)
      (format "%d_c*,%d_small_collections,-%d_collections*%s"
              index-set-id index-set-id index-set-id excluded-collections-str))))

(defn- get-granule-indexes
  "Returns the granule indexes that should be searched based on the input query"
  [context query]
  (let [coll-concept-ids (seq (cex/extract-collection-concept-ids query))
        provider-ids (seq (pex/extract-provider-ids query))]
    (cond
      coll-concept-ids
      ;; Use collection concept ids to limit the indexes queried
      (string/join "," (collection-concept-ids->index-names context coll-concept-ids))

      provider-ids
      ;; Use provider ids to limit the indexes queried
      (string/join "," (provider-ids->index-names context provider-ids))

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
                 (collections-index-alias))
   :type-name "collection"})

(defmethod common-esi/concept-type->index-info :autocomplete
  [context _ query]
  {:index-name "1_autocomplete"
   :type-name "suggestion"})

(defmethod common-esi/concept-type->index-info :tag
  [context _ query]
  {:index-name "1_tags"
   :type-name "tag"})

(defmethod common-esi/concept-type->index-info :variable
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 "1_all_variable_revisions"
                 "1_variables")
   :type-name "variable"})

(defmethod common-esi/concept-type->index-info :service
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 "1_all_service_revisions"
                 "1_services")
   :type-name "service"})

(defmethod common-esi/concept-type->index-info :tool
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 "1_all_tool_revisions"
                 "1_tools")
   :type-name "tool"})

(defmethod common-esi/concept-type->index-info :subscription
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 "1_all_subscription_revisions"
                 "1_subscriptions")
   :type-name "subscription"})

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod common-esi/concept-type->index-info concept-type
    [context _ query]
    {:index-name (if (:all-revisions? query)
                   (format "1_all_generic_%s_revisions" (string/replace (name concept-type)
                                                                        #"-" "_"))
                   (format "1_generic_%s" (string/replace (name concept-type)
                                                          #"-" "_")))
     :type-name (name concept-type)}))

(defn- context->conn
  [context]
  (get-in context [:system :search-index :conn]))

(comment defn- get-collection-permitted-groups
  "NOTE: Use for debugging only. Gets collections along with their currently permitted groups. This
   won't work if more than 10,000 collections exist in the CMR.
   called by dev-system/src/cmr/dev_system/control.clj only"
  [context]
  (let [index-info (common-esi/concept-type->index-info context :collection nil)
        results (esd/search (context->conn context)
                            (:index-name index-info)
                            [(:type-name index-info)]
                            :query (q/match-all)
                            :size 10000
                            :_source ["permitted-group-ids"])
        hits (get-in results [:hits :total :value])]
    (when (> hits (count (get-in results [:hits :hits])))
      (e/internal-error! "Failed to retrieve all hits."))
    (into {} (for [hit (get-in results [:hits :hits])]
               [(:_id hit) (get-in hit [:_source :permitted-group-ids])]))))

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
                                                                  :collection-concept-seq-id-long
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
