(ns cmr.search.services.query-execution.has-granules-or-cwic-results-feature
  "This enables the :has-granules-or-cwic and :has-granules-or-opensearch feature for collection
   search results. When it is enabled collection search results will include a boolean flag
   indicating whether the collection has any granules at all as indicated by provider holdings."
  (:require
   [cmr.common-app.config :as common-config]
   [cmr.common.cache :as cache]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]
   [cmr.elastic-utils.search.es-index :as common-esi]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.search.data.elastic-search-index :as idx]))

(def has-granules-or-cwic-cache-key
  :has-granules-or-cwic-map)

(def has-granules-or-opensearch-cache-key
  :has-granules-or-opensearch-map)

(defn create-has-granules-or-cwic-map-cache
  "Returns a 'cache' which will contain the cached has granules map."
  []
  (redis-cache/create-redis-cache {:keys-to-track [has-granules-or-cwic-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn create-has-granules-or-opensearch-map-cache
  "Returns a 'cache' which will contain the cached has granules map."
  []
  (redis-cache/create-redis-cache {:keys-to-track [has-granules-or-opensearch-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn get-cwic-collections
  "Returns any CWIC collections and sets the granule count to 1 so that these collections
  are included with the query."
  [context]
  (let [condition (qm/string-conditions :consortiums ["CWIC"])
        query (qm/query {:concept-type :collection
                         :condition condition
                         :page-size :unlimited
                         :remove-source true})
        results (common-esi/execute-query context query)]
    (into {}
          (for [coll-id (map :_id (get-in results [:hits :hits]))]
            [coll-id 1]))))

(defn get-opensearch-collections
  "Returns any Opensearch collections and sets the granule count to 1 so that these collections
  are included with the query."
  [context]
  (let [condition (gc/or-conds (map #(qm/string-conditions :consortiums [%])
                                    (common-config/opensearch-consortiums)))
        query (qm/query {:concept-type :collection
                         :condition condition
                         :page-size :unlimited
                         :remove-source true})
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

(defn create-has-granules-or-cwic-map
  "Creates the has granules or cwic map by combining all collections
  that have granules with CWIC collections. The map looks like the following:
  {\"C0000000002-PROV1\" true, \"C0000000003-PROV1\" true}"
  [context]
  (collection-granule-counts->has-granules-or-cwic-map
   (merge
    (idx/get-collection-granule-counts context nil)
    (get-cwic-collections context))))

(defn create-has-granules-or-opensearch-map
  "Creates the has granules or opensearch map by combining all collections
  that have granules with Opensearch collections. The map looks like the following:
  {\"C0000000002-PROV1\" true, \"C0000000003-PROV1\" true}"
  [context]
  (collection-granule-counts->has-granules-or-opensearch-map
   (merge
    (idx/get-collection-granule-counts context nil)
    (get-opensearch-collections context))))

(defn refresh-has-granules-or-cwic-map
  "Gets the latest provider holdings and updates the has-granules-or-cwic-map stored in the cache."
  [context]
  (let [has-granules-or-cwic-map (create-has-granules-or-cwic-map context)]
    (cache/set-value (cache/context->cache context has-granules-or-cwic-cache-key)
                     has-granules-or-cwic-cache-key
                     has-granules-or-cwic-map)))

(defn refresh-has-granules-or-opensearch-map
  "Gets the latest provider holdings and updates the has-granules-or-opensearch-map stored in the cache."
  [context]
  (let [has-granules-or-opensearch-map (create-has-granules-or-opensearch-map context)]
    (cache/set-value (cache/context->cache context has-granules-or-opensearch-cache-key)
                     :has-granules-or-opensearch has-granules-or-opensearch-map)))

(defn get-has-granules-or-cwic-map
  "Gets the cached has granules map from the context which contains collection ids to true or false
  of whether the collections have granules or not. If the has-granules-or-cwic-map has not yet been cached
  it will retrieve it and cache it.
  Example) {\"C0000000002-PROV1\" true, \"C0000000003-PROV1\" true}"
  [context]
  (let [has-granules-or-cwic-map-cache (cache/context->cache context has-granules-or-cwic-cache-key)]
    (cache/get-value has-granules-or-cwic-map-cache
                     has-granules-or-cwic-cache-key
                     (fn [] (create-has-granules-or-cwic-map context)))))

(defn get-has-granules-or-opensearch-map
  "Gets the cached has granules map from the context which contains collection ids to true or false
  of whether the collections have granules or not. If the has-granules-or-opensearch-map has not yet been cached
  it will retrieve it and cache it."
  [context]
  (let [has-granules-or-opensearch-map-cache (cache/context->cache context has-granules-or-opensearch-cache-key)]
    (cache/get-value has-granules-or-opensearch-map-cache
                     :has-granules-or-opensearch
                     (fn []
                       (create-has-granules-or-opensearch-map context)))))
