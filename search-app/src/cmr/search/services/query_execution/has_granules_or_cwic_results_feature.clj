(ns cmr.search.services.query-execution.has-granules-or-cwic-results-feature
  "This enables the :has-granules-or-cwic and :has-granules-or-opensearch feature for collection search results.
  When it is enabled collection search results will include a boolean flag indicating whether the collection has
  any granules at all as indicated by provider holdings."
  (:require
   [clojure.string :as str]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.services.search.elastic-search-index :as common-esi]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.jobs :refer [defjob]]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.cache.consistent-cache :as consistent-cache]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.search.data.elastic-search-index :as idx]))

(def REFRESH_HAS_GRANULES_OR_CWIC_MAP_JOB_INTERVAL
  "The frequency in seconds of the refresh-has-granules-or-cwic-map-job"
  ;; default to 1 hour
  3600)

(def has-granules-or-cwic-cache-key
  :has-granules-or-cwic-map)

(def has-granules-or-opensearch-cache-key
  :has-granules-or-opensearch-map)

(defn create-has-granules-or-cwic-map-cache
  "Returns a 'cache' which will contain the cached has granules map."
  []
  ;; Single threaded lookup cache used to prevent indexing multiple items at the same time with
  ;; empty cache cause lots of lookups in elasticsearch.
  (stl-cache/create-single-thread-lookup-cache
    ;; Use the fall back cache so that the data is fast and available in memory
    ;; But if it's not available we'll fetch it from redis.
    (fallback-cache/create-fallback-cache

      ;; Consistent cache is required so that if we have multiple instances of the indexer we'll
      ;; have only a single indexer refreshing its cache.
      (consistent-cache/create-consistent-cache
       {:hash-timeout-seconds (* 5 60)})
      (redis-cache/create-redis-cache))))

(defn create-has-granules-or-opensearch-map-cache
  "Returns a 'cache' which will contain the cached has granules map."
  []
  ;; Single threaded lookup cache used to prevent indexing multiple items at the same time with
  ;; empty cache cause lots of lookups in elasticsearch.
  (stl-cache/create-single-thread-lookup-cache
    ;; Use the fall back cache so that the data is fast and available in memory
    ;; But if it's not available we'll fetch it from redis.
    (fallback-cache/create-fallback-cache

      ;; Consistent cache is required so that if we have multiple instances of the indexer we'll
      ;; have only a single indexer refreshing its cache.
      (consistent-cache/create-consistent-cache
       {:hash-timeout-seconds (* 5 60)})
      (redis-cache/create-redis-cache))))

(defn get-cwic-collections
  "Returns the collection granule count by searching elasticsearch by aggregation"
  [context provider-ids]
  (let [condition (qm/string-conditions :consortiums ["CWIC"])
        query (qm/query {:concept-type :collection
                         :condition condition
                         :page-size :unlimited})
        results (common-esi/execute-query context query)]
    (into {}
          (for [coll-id (map :_id (get-in results [:hits :hits]))]
            [coll-id 1]))))

(defn get-opensearch-collections
  "Returns the collection granule count by searching elasticsearch by aggregation"
  [context provider-ids]
  (let [condition (gc/or-conds (map #(qm/string-conditions :consortiums [%])
                                    (common-config/opensearch-consortiums)))
        query (qm/query {:concept-type :collection
                         :condition condition
                         :page-size :unlimited})
        results (common-esi/execute-query context query)]
    (into {}
          (for [coll-id (map :_id (get-in results [:hits :hits]))]
            [coll-id 1]))))

(defn- collection-granule-counts->has-granules-or-cwic-map
  "Converts a map of collection ids to granule or cwic counts to a map of collection ids to true or false
  of whether the collection has any granules or cwic"
  [coll-gran-counts]
  (into {} (for [[coll-id num-granules] coll-gran-counts]
             [coll-id (> num-granules 0)])))

(defn- collection-granule-counts->has-granules-or-opensearch-map
  "Converts a map of collection ids to granule or opensearch counts to a map of collection ids to true or false
  of whether the collection has any granules or opensearch"
  [coll-gran-counts]
  (into {} (for [[coll-id num-granules] coll-gran-counts]
             [coll-id (> num-granules 0)])))

(defn refresh-has-granules-or-cwic-map
  "Gets the latest provider holdings and updates the has-granules-or-cwic-map stored in the cache."
  [context]
  (let [has-granules-or-cwic-map (collection-granule-counts->has-granules-or-cwic-map
                                  (merge
                                   (idx/get-collection-granule-counts context nil)
                                   (get-cwic-collections context nil)))]
    (cache/set-value (cache/context->cache context has-granules-or-cwic-cache-key)
                     :has-granules-or-cwic has-granules-or-cwic-map)))

(defn refresh-has-granules-or-opensearch-map
  "Gets the latest provider holdings and updates the has-granules-or-opensearch-map stored in the cache."
  [context]
  (let [has-granules-or-opensearch-map (collection-granule-counts->has-granules-or-opensearch-map
                                        (merge
                                         (idx/get-collection-granule-counts context nil)
                                         (get-opensearch-collections context nil)))]
    (cache/set-value (cache/context->cache context has-granules-or-opensearch-cache-key)
                     :has-granules-or-opensearch has-granules-or-opensearch-map)))

(defn get-has-granules-or-cwic-map
  "Gets the cached has granules map from the context which contains collection ids to true or false
  of whether the collections have granules or not. If the has-granules-or-cwic-map has not yet been cached
  it will retrieve it and cache it."
  [context]
  (let [has-granules-or-cwic-map-cache (cache/context->cache context has-granules-or-cwic-cache-key)]
    (cache/get-value has-granules-or-cwic-map-cache
                     :has-granules-or-cwic
                     (fn []
                       (collection-granule-counts->has-granules-or-cwic-map
                        (merge
                         (idx/get-collection-granule-counts context nil)
                         (get-cwic-collections context nil)))))))

(defn get-has-granules-or-opensearch-map
  "Gets the cached has granules map from the context which contains collection ids to true or false
  of whether the collections have granules or not. If the has-granules-or-opensearch-map has not yet been cached
  it will retrieve it and cache it."
  [context]
  (let [has-granules-or-opensearch-map-cache (cache/context->cache context has-granules-or-opensearch-cache-key)]
    (cache/get-value has-granules-or-opensearch-map-cache
                     :has-granules-or-opensearch
                     (fn []
                       (collection-granule-counts->has-granules-or-opensearch-map
                        (merge
                         (idx/get-collection-granule-counts context nil)
                         (get-opensearch-collections context nil)))))))

(defjob RefreshHasGranulesOrCwicMapJob
  [ctx system]
  (refresh-has-granules-or-cwic-map {:system system}))

(defjob RefreshHasGranulesOrOpenSearchMapJob
  [ctx system]
  (refresh-has-granules-or-opensearch-map {:system system}))

(def refresh-has-granules-or-cwic-map-job
  {:job-type RefreshHasGranulesOrCwicMapJob
   :interval REFRESH_HAS_GRANULES_OR_CWIC_MAP_JOB_INTERVAL})

(def refresh-has-granules-or-opensearch-map-job
  {:job-type RefreshHasGranulesOrOpenSearchMapJob
   :interval REFRESH_HAS_GRANULES_OR_CWIC_MAP_JOB_INTERVAL})
