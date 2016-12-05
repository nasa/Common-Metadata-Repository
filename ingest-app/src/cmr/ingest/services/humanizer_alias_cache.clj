(ns cmr.ingest.services.humanizer-alias-cache
  "Stores the latest humanizer platform alias map in a cache.
   The keys are the platform shortnames and the value for each key is
   a list of platform alias shortnames for that given platform shortname."
  (:require
   [clojure.set :as set]
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
   {\"TERRA\" [\"AM-1\" \"am-1\" \"AM 1\"] \"OTHERPLATFORMS\" [\"otheraliases\"]}"
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

(defn- get-humanizer-platform-alias-map
  "Returns the humanizer alias map"
  [context]
  (let [cache (cache/context->cache context humanizer-alias-cache-key)
        humanizer (humanizer/get-humanizers context)]
    (cache/get-value cache
                 humanizer-alias-cache-key
                 #(retrieve-humanizer-platform-alias-map humanizer))))

(defn update-collection-with-platform-aliases
  "Returns the collection with all the platform aliases added."
  [context collection plat-key plat-sn-key]
  ;; Original collection platforms: [{:ShortName "Terra" :Otherfields "other values"}
  ;;                                 {:ShortName "AM-1" :Otherfields "other values"}]
  ;; updated collection platforms: [{:ShortName "Terra" :Otherfields "other values"}
  ;;                                {:ShortName "AM-1" :Otherfields "other values"}
  ;;                                {:ShortName "am-1" :Otherfields "other values"}]
  ;; Note: For each collection platform, the for loop is looping through all the aliases
  ;; that don't exist in the platform-shortnames. In this case, Terra's alias AM-1 won't
  ;; be added to the updated collection because it already exists in the platform-shortnames. 
  (let [platforms (plat-key collection)
        platform-shortnames (map plat-sn-key platforms)
        plat-alias-map (get-humanizer-platform-alias-map context)
        platform-aliases (for [coll-plat platforms
                               :let [coll-plat-sn (plat-sn-key coll-plat)
                                     aliases (get plat-alias-map (str/upper-case coll-plat-sn))]
                               alias (set/difference (set aliases) (set platform-shortnames))]
                           (assoc coll-plat plat-sn-key alias))
        updated-collection (update collection plat-key concat platform-aliases)]
    updated-collection ))
