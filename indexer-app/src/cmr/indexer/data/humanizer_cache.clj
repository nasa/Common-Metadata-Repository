(ns cmr.indexer.data.humanizer-cache
  "Stores the latest humanizer json in a consistent cache."
  (require [cmr.common.jobs :refer [def-stateful-job]]
           ;; cache dependencies
           [cmr.common.cache :as c]
           [cmr.common.cache.fallback-cache :as fallback-cache]
           [cmr.common-app.cache.cubby-cache :as cubby-cache]
           [cmr.common-app.cache.consistent-cache :as consistent-cache]
           [cmr.common.cache.single-thread-lookup-cache :as stl-cache]

           [cmr.transmit.search :as search]))

(def humanizer-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  ;; Single threaded lookup cache used to prevent indexing multiple items at the same time with
  ;; empty cache cause lots of lookups in elasticsearch.
  (stl-cache/create-single-thread-lookup-cache
    ;; Use the fall back cache so that the data is fast and available in memory
    ;; But if it's not available we'll fetch it from cubby.
    (fallback-cache/create-fallback-cache

      ;; Consistent cache is required so that if we have multiple instances of the indexer we'll have
      ;; only a single indexer refreshing it's cache.
      (consistent-cache/create-consistent-cache)
      (cubby-cache/create-cubby-cache))))

(defn refresh-cache
  "Refreshes the humanizer in the cache."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)]
    (c/set-value cache humanizer-cache-key
                 (search/get-humanizer context))))

(defn get-humanizer
  "Returns the humanizer."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)]
    (c/get-value cache
                 humanizer-cache-key
                 #(search/get-humanizer context))))


