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

(defn- retrieve-humanizer-alias-map
  "Returns the alias map like 
   {\"platform\" {\"TERRA\" [\"AM-1\" \"am-1\" \"AM 1\"] \"OTHERPLATFORMS\" [\"otheraliases\"]}
    \"tiling_system_name\" {\"TILE\" [\"tile_1\" \"tile_2\"] \"OTHERTILES\" [\"otheraliases\"]}}"
  [humanizer]
  (into {}
        (for [[k1 v1] 
              (group-by :field
                (map #(select-keys % [:field :replacement_value :source_value])
                  (filter #(= (:type %) "alias") humanizer)))]
          [k1 (into {}
                    (for [[k2 v2] 
                          (group-by :replacement_value
                            (map #(select-keys % [:replacement_value :source_value]) v1))]
                      [(str/upper-case k2) (map :source_value v2)]))])))    

(defn refresh-cache
  "Refreshes the humanizer alias cache."
  [context]
  (let [cache (cache/context->cache context humanizer-alias-cache-key)
        humanizer (humanizer/get-humanizers context)]
    (cache/set-value cache humanizer-alias-cache-key
                 (retrieve-humanizer-alias-map humanizer))))

(defn- get-humanizer-alias-map
  "Returns the humanizer alias map"
  [context]
  (let [cache (cache/context->cache context humanizer-alias-cache-key)
        humanizer (humanizer/get-humanizers context)]
    (cache/get-value cache
                 humanizer-alias-cache-key
                 #(retrieve-humanizer-alias-map humanizer))))

(defn- get-field-aliases
  "Returns field aliases for a given collection's fields, field-name-key and a field-alias-map.
   Note: if the field-name of a field alias already exists in the collection's fields, this
   alias won't be added to the field-aliases."
  [fields field-name-key field-alias-map]
  (let [field-names-set (set (map field-name-key fields))
        field-aliases 
         (for [coll-field fields
               :let [coll-field-name (get coll-field field-name-key)
                     aliases-set (set (get field-alias-map (str/upper-case coll-field-name)))]
                alias (set/difference aliases-set field-names-set)]
           (assoc coll-field field-name-key alias))]
    field-aliases)) 

(defn update-collection-with-aliases
  "Returns the collection with all the platform and tile aliases added.
   Using platform aliases as an example:
   Given plat-alias-map being {\"TERRA\" [\"AM-1\" \"am-1\"]}, 
   which indicates AM-1 and am-1 are aliases of TERRA. That means that granules referring to 
   a collection through AM-1 or am-1 would be permitted if the collection had TERRA.
   Here is one example:  
   Original collection platforms: [{:ShortName \"Terra\" :Otherfields \"other-terra-values\"}
                                   {:ShortName \"AM-1\" :Otherfields \"other-am-1-values\"}]
   updated collection platforms: [{:ShortName \"Terra\" :Otherfields \"other-terra-values\"}
                                  {:ShortName \"AM-1\" :Otherfields \"other-am-1-values\"}
                                  {:ShortName \"am-1\" :Otherfields \"other-terra-values\"}]
   Note: If the ShortName of a platform alias already exists in the original collection platforms 
   We will keep the original one and not add the alias to the updated collection platforms. 
   In the above example, alias {:ShortName \"AM-1\" :Otherfields \"other-terra-values\"} is not added.
   This is a conscious decision, not a bug." 
  [context collection umm-spec-collection?]
  (let [plat-key (if umm-spec-collection?
                   :Platforms
                   :platforms)
        plat-name-key (if umm-spec-collection?
                        :ShortName
                        :short-name)
        tile-key (if umm-spec-collection?
                   :TilingIdentificationSystems
                   :two-d-coordinate-systems)
        tile-name-key (if umm-spec-collection?
                        :TilingIdentificationSystemName
                        :name)
        alias-map (get-humanizer-alias-map context) 
        platforms (get collection plat-key)
        plat-alias-map (get alias-map "platform")
        platform-aliases (get-field-aliases platforms plat-name-key plat-alias-map)
        tiles (get collection tile-key)
        tile-alias-map (get alias-map "tiling_system_name")
        tile-aliases (get-field-aliases tiles tile-name-key tile-alias-map)
        updated-collection (-> collection
                               (update plat-key concat platform-aliases)
                               (update tile-key concat tile-aliases))]
    updated-collection))
