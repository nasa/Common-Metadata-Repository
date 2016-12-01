(ns cmr.ingest.services.humanizer-alias-cache
  "Stores the latest humanizer platform alias map in a cache.
   The keys are the platform shortnames and the value for each key is
   a list of platform alias shortnames for that given platform shortname."
  (:require
   [clojure.string :as str]
   [cmr.common.cache :as cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.transmit.humanizer :as humanizer]))

(def humanizer-alias-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-alias-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache))

(defn- retrieve-humanizer-platform-alias-map
  "Returns the platform alias map like 
   {\"Terra\" [\"AM-1\" \"am-1\" \"AM 1\"] \"Otherplatforms\" [\"otheraliases\"]}"
  [humanizer]
  (into {}
        (for [[k v] (group-by :replacement_value
                      (map #(select-keys % [:replacement_value :source_value])
                        (filter #(and (= (:type %) "alias") (= (:field %) "platform"))
                          humanizer)))]
          [(str/upper-case k) (map :source_value v)])))

(defn refresh-cache
  "Refreshes the humanizer alias cache."
  [context]
  (let [cache (cache/context->cache context humanizer-alias-cache-key)
        humanizer (humanizer/get-humanizers context)]
    (cache/set-value cache humanizer-alias-cache-key
                 (retrieve-humanizer-platform-alias-map humanizer))))

(defn get-humanizer-platform-alias-map
  "Returns the humanizer alias map"
  [context]
  (let [cache (cache/context->cache context humanizer-alias-cache-key)
        humanizer (humanizer/get-humanizers context)]
    (cache/get-value cache
                 humanizer-alias-cache-key
                 #(retrieve-humanizer-platform-alias-map humanizer))))
