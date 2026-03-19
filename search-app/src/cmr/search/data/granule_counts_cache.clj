(ns cmr.search.data.granule-counts-cache
  (:require
   [cmr.common.log :as log]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.services.errors :as e]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-query-to-elastic :as q2e]
   [cmr.elastic-utils.search.es-index :as common-esi]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-cache :as redis-cache]))

(def granule-counts-cache-key
  :granule-counts-cache)

(defn create-granule-counts-cache-client
  "Creates and returns a new cache for storing granule counts."
  []
  (redis-cache/create-redis-cache {:keys-to-track [granule-counts-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn get-collection-granule-counts
  "Returns the collection granule count by searching elasticsearch by aggregation"
  [context]
  (let [query (qm/query {:concept-type :granule
                         :condition qm/match-all
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
      (e/internal-error! (format "There were [%s] more providers with granules than we ever expected to see." extra-provider-count)))

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

(defn refresh-granule-counts-cache
  "Refreshes the granule counts cache with the latest data. This is called from a lambda
   triggered by an event bridge schedule."
  ([context] 
   (refresh-granule-counts-cache context get-collection-granule-counts))
  ([context func]
   (log/info "Starting refresh-granule-counts-cache")
   (let [granule-counts (func context)
         cache (cache/context->cache context granule-counts-cache-key)]
     (cache/set-value cache granule-counts-cache-key granule-counts)
     (log/info "Finished refresh-granule-counts-cache"))))

(defn get-granule-counts
  "Retrieves the cached granule counts, or fetches them if not cached."
  ([context]
   (get-granule-counts context nil get-collection-granule-counts))
  ([context provider-ids]
   (get-granule-counts context provider-ids get-collection-granule-counts))
  ([context provider-ids get-collection-granule-counts-fn]
   (let [cache (cache/context->cache context granule-counts-cache-key)
         counts (cache/get-value cache
                                 granule-counts-cache-key
                                 #(get-collection-granule-counts-fn context))]
     (if (seq provider-ids)
       (let [allowed-set (set provider-ids)]
         (into {} (filter (fn [[k _]]
                            (allowed-set (concepts/concept-id->provider-id k)))
                          counts)))
       counts))))

     ;; (if (seq provider-ids)
     ;;   (into {} (filter (fn [[k _]]
     ;;                      (some #(= (concepts/concept-id->provider-id k) %) provider-ids))
     ;;                    counts))
     ;;   counts)
     ;; )))
