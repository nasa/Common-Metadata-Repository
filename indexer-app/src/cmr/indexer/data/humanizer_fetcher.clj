(ns cmr.indexer.data.humanizer-fetcher
  "Stores the latest humanizer json in a consistent cache."
  (:require
   [cmr.common.cache :as c]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.transmit.humanizer :as humanizer]))

(def humanizer-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache))

(defn- retrieve-humanizers
  [context]
  (humanizer/get-humanizers context))

(defn refresh-cache
  "Refreshes the humanizers in the cache."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)]
    (c/set-value cache humanizer-cache-key
                 (retrieve-humanizers context))))

(defn get-humanizer-instructions
  "Returns the humanizer instructions."
  [context]
  (let [cache (c/context->cache context humanizer-cache-key)]
    (c/get-value cache
                 humanizer-cache-key
                 #(retrieve-humanizers context))))
