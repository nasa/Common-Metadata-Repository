(ns cmr.indexer.data.humanizer-fetcher
  "Stores the latest humanizer json in a consistent cache."
  (:require
   [cmr.common.cache :as c]
   [cmr.common.log :refer [info]]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.humanizer :as humanizer]))

(def humanizer-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (redis-cache/create-redis-cache {:keys-to-track [humanizer-cache-key]}))

(defn- retrieve-humanizers
  [context]
  (humanizer/get-humanizers context))

(defn refresh-cache
  "Refreshes the humanizers in the cache."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)
        start (System/currentTimeMillis)
        humanizers (retrieve-humanizers context)
        _ (info "Refreshing :humanizer-cache - saving to redis.")
        ;result (c/set-value cache humanizer-cache-key (retrieve-humanizers context))
        result (c/set-value cache humanizer-cache-key humanizers)]
    (info (format "Redis timed function refresh-cache for %s redis set-value time [%s] ms " humanizer-cache-key (- (System/currentTimeMillis) start)))
    result))

(defn get-humanizer-instructions
  "Returns the humanizer instructions."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)
        start (System/currentTimeMillis)
        _ (info "Reading :humanizer-cache get-humanizer-instructions, if it can't read the cache it gets the data and loads the cache.")
        result (c/get-value cache
                            humanizer-cache-key
                            #(retrieve-humanizers context))]
    (info (format "Redis timed function get-humanizer-instructions for %s redis set-value time [%s] ms " humanizer-cache-key (- (System/currentTimeMillis) start)))
    result))
