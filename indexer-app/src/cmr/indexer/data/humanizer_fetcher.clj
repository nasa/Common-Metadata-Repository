(ns cmr.indexer.data.humanizer-fetcher
  "Stores the latest humanizer json in a consistent cache."
  (:require
   [cmr.common.cache :as c]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.util :as util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.humanizer :as humanizer]))

(def humanizer-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-cache)

(defn create-cache-client
  "Creates an instance of the cache."
  []
  (redis-cache/create-redis-cache {:keys-to-track [humanizer-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn- retrieve-humanizers
  [context]
  (humanizer/get-humanizers context))

(defn refresh-cache
  "Refreshes the humanizers in the cache."
  [context]
  (rl-util/log-refresh-start humanizer-cache-key)
  (let [cache (c/context->cache context humanizer-cache-key)
        humanizers (retrieve-humanizers context)
        [tm _] (util/time-execution
                (c/set-value cache humanizer-cache-key humanizers))]
    (rl-util/log-redis-write-complete "refresh-cache" humanizer-cache-key tm)))

(defn get-humanizer-instructions
  "Returns the humanizer instructions."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)
        [tm value] (util/time-execution (c/get-value cache humanizer-cache-key))
        _ (rl-util/log-redis-read-complete "get-humanizer-instructions" humanizer-cache-key tm)]
    (if value
      value
      (let [humanizers (retrieve-humanizers context)
            [tm _] (util/time-execution (c/set-value cache humanizer-cache-key humanizers))
            _ (rl-util/log-redis-write-complete "get-humanizer-instructions" humanizer-cache-key tm)]
        humanizers))))
