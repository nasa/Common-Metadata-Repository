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

(defn- get-platform-aliases
  "Returns platform aliases for a given collection's platforms, plat-sn-key and a plat-alias-map.
   Note: if the shortname of a platform alias already exists in the collection's platforms, this
   alias won't be added to the platform-aliases."
  [platforms plat-sn-key plat-alias-map]
  (let [platform-shortnames (map plat-sn-key platforms)
        platform-aliases (for [coll-plat platforms
                               :let [coll-plat-sn (get coll-plat plat-sn-key)
                                     aliases (get plat-alias-map (str/upper-case coll-plat-sn))]
                               alias (set/difference (set aliases) (set platform-shortnames))]
                           (assoc coll-plat plat-sn-key alias))]
    platform-aliases)) 

(defn update-collection-with-aliases
  "Returns the collection with all the platform aliases added.
   Given plat-alias-map being {\"TERRA\" [\"AM-1\" \"am-1\"]} 
   Original collection platforms: [{:ShortName \"Terra\" :Otherfields \"other-terra-values\"}
                                   {:ShortName \"AM-1\" :Otherfields \"other-am-1-values\"}]
   updated collection platforms: [{:ShortName \"Terra\" :Otherfields \"other-terra-values\"}
                                  {:ShortName \"AM-1\" :Otherfields \"other-am-1-values\"}
                                  {:ShortName \"am-1\" :Otherfields \"other-terra-values\"}]
   Note: If the ShortName of a platform alias already exists in the original collection platforms 
   We will keep the original one and not add the alias to the updated collection platforms. 
   In the above example, alias {:ShortName \"AM-1\" :Otherfields \"other-terra-values\"} is not added.
   This is a conscious decision, not a bug." 
  [context collection plat-key plat-sn-key]
  (let [platforms (get collection plat-key)
        plat-alias-map (get-humanizer-platform-alias-map context)
        platform-aliases (get-platform-aliases platforms plat-sn-key plat-alias-map)
        updated-collection (update collection plat-key concat platform-aliases)]
    updated-collection))
