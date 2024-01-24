(ns cmr.common-app.data.humanizer-cache
		(:require
				[cmr.common.cache :as cmn-cache]
				[cmr.common.log :as log :refer [info]]
				[cmr.redis-utils.redis-cache :as redis-cache]
				[cmr.transmit.humanizer :as humanizer]))

(def cache-key
    "The cache key to use when storing with caches in the system."
    :humanizer-cache)

(defn create-cache-client
    "Creates an instance of the cache."
    []
    (redis-cache/create-redis-cache {:keys-to-track [cache-key]}))

(defn- retrieve-humanizers
		[context]
		(humanizer/get-humanizers context))

(defn refresh-cache
		"Refreshes the humanizers in the cache."
		[context]
		(info "Refreshing humanizer cache")
		(let [cache (cmn-cache/context->cache context cache-key)]
				(cmn-cache/set-value cache cache-key
																									(retrieve-humanizers context))
				(info "Humanizer cache refresh complete.")))

(defn get-humanizer-instructions
		"Returns the humanizer instructions."
		[context]
		(let [cache (cmn-cache/context->cache context cache-key)]
				(cmn-cache/get-value cache
																									cache-key
																									#(retrieve-humanizers context))))
