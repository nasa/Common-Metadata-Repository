(ns cmr.common-app.cache.humanizer-map-fetcher
  "Stores the latest humanizer alias map in a cache."
  (:require
   [clojure.string :as str]
   [cmr.common.cache :as c]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.transmit.humanizer :as humanizer]))

(def humanizer-map-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache))

(defn- retrieve-humanizers
  [context]
  (humanizer/get-humanizers context))

(defn- retrieve-humanizer-platform-alias-map
  "returns the platform alias map"
  [context]
  (let [humanizer (retrieve-humanizers context)]
    (into {}
          (for [[k v] (group-by :replacement_value
                        (map #(select-keys % [:replacement_value :source_value])
                          (filter #(and (= (:type %) "alias") (= (:field %) "platform"))
                            humanizer)))]
            [(str/upper-case k) (mapcat vals (map #(select-keys % [:source_value]) v))]))))

(defn refresh-cache
  "Refreshes the humanizers in the cache."
  [context]
  (let [cache (c/context->cache context humanizer-map-cache-key)]
    (c/set-value cache humanizer-map-cache-key
                 (retrieve-humanizer-platform-alias-map context))))

(defn get-humanizer-platform-alias-map
  "Returns the humanizer alias map"
  [context]
  (let [cache (c/context->cache context humanizer-map-cache-key)]
    (c/get-value cache
                 humanizer-map-cache-key
                 #(retrieve-humanizer-platform-alias-map context))))
