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

(defn create-redis-cache-client
  "Creates and returns a new cache for storing granule counts."
  []
  (redis-cache/create-redis-cache {:keys-to-track [granule-counts-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

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
    (when (> extra-provider-count 0)
      (e/internal-error! (str "There were [" extra-provider-count "] more providers with granules than we ever expected to see.")))

    (into {} (for [provider-bucket (get-in results [:aggregations :by-provider :buckets])
                   :let [provider-id (:key provider-bucket)
                         extra-collection-count (get-in provider-bucket [:by-collection-id :sum_other_doc_count])
                         _ (when (> extra-collection-count 0)
                             (e/internal-error!
                              (format "Provider %s has more collections ([%s]) with granules than we support"
                                      provider-id
                                      extra-collection-count)))
                         coll-buckets (get-in provider-bucket [:by-collection-id :buckets])]]
               (for [coll-bucket coll-buckets
                     :let [coll-seq-id (:key coll-bucket)
                           num-granules (:doc_count coll-bucket)]]
                 [(concepts/build-concept-id {:sequence-number coll-seq-id
                                              :provider-id provider-id
                                              :concept-type :collection})
                  num-granules])))))

(defn refresh-granule-counts-cache
  "This is called from lambda that is triggered by an event bridge schedule refreshes the granule counts cache with the latest data."
  ([context]
   (refresh-granule-counts-cache context #(get-collection-granule-counts context nil)))
  ([context func]
   (let [granule-counts (func)
         cache (cache/context->cache context granule-counts-cache-key)]
     (log/info "Refreshing granule counts cache with" (count granule-counts) "entries")
     (if cache
       (cache/set-value cache granule-counts-cache-key granule-counts)
       (let [error-msg "Granule counts cache not found in context - refresh skipped"]
         (log/error error-msg)
         (throw (IllegalStateException. error-msg)))))))

(defn get-granule-counts
  "Retrieves the cached granule counts, or fetches them if not cached."
  ([context]
   (get-granule-counts context nil get-collection-granule-counts))
  ([context provider-ids]
   (get-granule-counts context provider-ids get-collection-granule-counts))
  ([context provider-ids get-collection-granule-counts-fn]
   (cache/get-value (cache/context->cache context granule-counts-cache-key)
                    granule-counts-cache-key
                    #(get-collection-granule-counts-fn context provider-ids))))

(defn clear-granule-counts-cache
  "Clears the granule counts cache."
  [context]
  (let [cache (cache/context->cache context granule-counts-cache-key)]
    (if cache
      (cache/reset cache)
      (let [error-msg "Granule counts cache not found in context - clear skipped"]
        (log/error error-msg)
        (throw (IllegalStateException. error-msg))))))

