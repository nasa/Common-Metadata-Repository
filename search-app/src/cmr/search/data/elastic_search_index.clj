(ns cmr.search.data.elastic-search-index
  "Implements the search index protocols for searching against Elasticsearch."
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.hash-cache :as hcache]
   [cmr.common.services.errors :as e]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.es-index-helper :as esi-helper]
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

(defn- collection-concept-id->index-name
  "Return the granule index name for the input collection concept id"
  [indexes coll-concept-id]
  (get indexes (keyword coll-concept-id) (get indexes :small_collections)))

(defn- collection-concept-ids->index-aliases
  "Return the granule index aliases for the input collection concept ids"
  [context coll-concept-ids]
  (let [indexes (get-granule-index-names context)]
    (distinct
     (map #(collection-concept-id->index-name indexes %)
          coll-concept-ids))))

(defn- provider-ids->index-aliases
  "Return the granule index aliases for the input provider-ids"
  [context provider-ids]
  (let [indexes (get-granule-index-names context)]
    (cons (esi-helper/index-alias (get indexes :small_collections))
          (map #(esi-helper/index-alias
                 (format "%d_c*_%s" index-set-id (string/lower-case %)))
               provider-ids))))

(defn- all-granule-index-aliases
  "Returns all possible granule index aliases in a string that can be used by elasticsearch query"
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
                                   (str "," (string/join "," (map #(str "-" % "*") rebalancing-indexes)))
                                   "")]
    (format "%d_c*_alias,%d_small_collections_alias,-%d_collections*%s"
            index-set-id index-set-id index-set-id excluded-collections-str)))

(defn- get-granule-index-aliases
  "Returns the granule index aliases that should be searched based on the input query"
  [context query]
  (let [coll-concept-ids (seq (cex/extract-collection-concept-ids query))
        provider-ids (seq (pex/extract-provider-ids query))]
    (cond
      coll-concept-ids
      ;; Use collection concept ids to limit the indexes queried
      (string/join "," (collection-concept-ids->index-aliases context coll-concept-ids))

      provider-ids
      ;; Use provider ids to limit the indexes queried
      (string/join "," (provider-ids->index-aliases context provider-ids))

      :else
      (all-granule-index-aliases context))))

(defmethod common-esi/concept-type->index-info :granule
  [context _ query]
  {:index-name (get-granule-index-aliases context query)
   :type-name "granule"})

(defmethod common-esi/concept-type->index-info :collection
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 (esi-helper/index-alias "1_all_collection_revisions")
                 (collections-index-alias))
   :type-name "collection"})

(defmethod common-esi/concept-type->index-info :autocomplete
  [context _ query]
  {:index-name (esi-helper/index-alias "1_autocomplete")
   :type-name "suggestion"})

(defmethod common-esi/concept-type->index-info :tag
  [context _ query]
  {:index-name (esi-helper/index-alias "1_tags")
   :type-name "tag"})

(defmethod common-esi/concept-type->index-info :variable
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 (esi-helper/index-alias "1_all_variable_revisions")
                 (esi-helper/index-alias "1_variables"))
   :type-name "variable"})

(defmethod common-esi/concept-type->index-info :service
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 (esi-helper/index-alias "1_all_service_revisions")
                 (esi-helper/index-alias "1_services"))
   :type-name "service"})

(defmethod common-esi/concept-type->index-info :tool
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 (esi-helper/index-alias "1_all_tool_revisions")
                 (esi-helper/index-alias "1_tools"))
   :type-name "tool"})

(defmethod common-esi/concept-type->index-info :subscription
  [context _ query]
  {:index-name (if (:all-revisions? query)
                 (esi-helper/index-alias "1_all_subscription_revisions")
                 (esi-helper/index-alias "1_subscriptions"))
   :type-name "subscription"})

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (let [clean-name (string/replace (name concept-type) #"-" "_")]
    (defmethod common-esi/concept-type->index-info concept-type
      [context _ query]
      (let [index-name (if (:all-revisions? query)
                         (esi-helper/index-alias (format "1_all_generic_%s_revisions" clean-name))
                         (esi-helper/index-alias (format "1_generic_%s" clean-name)))]
        {:index-name index-name
         :type-name  (name concept-type)}))))

(defn- context->conn
  [context es-cluster-name]
  (cond
    (= es-cluster-name es-config/elastic-name) (get-in context [:system :search-index :conn])
    (= es-cluster-name es-config/gran-elastic-name) (get-in context [:system :gran-search-index :conn])
    :else (throw (Exception. (es-config/invalid-elastic-cluster-name-msg es-cluster-name)))))

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
