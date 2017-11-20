(ns cmr.ingest.services.humanizer-alias-cache
  "Stores the latest humanizer platform alias map in a cache.
   The keys are the platform shortnames and the value for each key is
   a list of platform alias shortnames for that given platform shortname."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.cache :as cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.util :as util]
   [cmr.transmit.humanizer :as humanizer]))

(def humanizer-alias-cache-key
  "The cache key to use when storing with caches in the system."
  :humanizer-alias-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache))

(defn- humanizer-group-by-field
  "A custom group-by function for use in the create-humanizer-alias-map
  function."
  [humanizer]
  (group-by
    :field
    (map
      #(select-keys % [:field :replacement_value :source_value])
      (filter #(= (:type %) "alias") humanizer))))

(defn- humanizer-group-by-replacement-value
  "A custom group-by function for use in the create-humanizer-alias-map
  function.

  In particular, this function converts the value assciated with
  :replacement_value to upper-case before group-by in order to cover the case
  in test-humanizers.json where there are multiple :replacement_value that only
  differ in upper-lower cases."
  [v1]
  (group-by
    :replacement_value
    (->>  v1
          (map #(select-keys % [:replacement_value :source_value]))
          (map #(update % :replacement_value str/upper-case)))))

(defn- create-humanizer-alias-map
  "Creates a map of humanizer aliases by type from the humanizer map and returns in the format below.
   Note: All the replacement_value are UPPER-CASED, so when using this map to get
   all the non-humanized source values for a given collection's platform,
   tile, or instrument, they need to be UPPER-CASED as well.
   {\"platform\" {\"TERRA\" [\"AM-1\" \"am-1\" \"AM 1\"] \"OTHERPLATFORMS\" [\"otheraliases\"]}
    \"tiling_system_name\" {\"TILE\" [\"tile_1\" \"tile_2\"] \"OTHERTILES\" [\"otheraliases\"]}
    \"instrument\" {\"INSTRUMENT\" [\"instr1\" \"instr2\"] \"OTHERINSTRUMENTS\" [\"otheraliases\"]}}"
  [humanizer]
  (into
    {}
    (for [[k1 v1] (humanizer-group-by-field humanizer)]
      [k1 (into
            {}
            (for [[k2 v2] (humanizer-group-by-replacement-value v1)]
              [k2 (map :source_value v2)]))])))

(defn refresh-cache
  "Refreshes the humanizer alias cache."
  [context]
  (let [cache (cache/context->cache context humanizer-alias-cache-key)
        humanizer (humanizer/get-humanizers context)]
    (cache/set-value cache humanizer-alias-cache-key
                 (create-humanizer-alias-map humanizer))))

(defn get-humanizer-alias-map
  "Returns the humanizer alias map"
  [context]
  (let [cache (cache/context->cache context humanizer-alias-cache-key)]
    (cache/get-value cache
                 humanizer-alias-cache-key
                 #(create-humanizer-alias-map (humanizer/get-humanizers context)))))

(defn- get-field-aliases
  "Returns field aliases for a given element's fields, field-name-key and a field-alias-map.
   Note: if the field-name of a field alias already exists in the element's fields, this
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

(defn update-element-with-subelement-aliases
  "Returns the element with subelement aliases added.
   The element could be collection, platform, instrument,
   and the aliases would be added one level below:
   for collection: platform and tile aliases will be added.
       platform: instrument aliases will be added.
       instrument: sensor aliases will be added(not implemented)"
  [element subelement-key subelement-name-key subelement-alias-map]
  (let [subelements (get element subelement-key)
        subelement-aliases (get-field-aliases
                             subelements subelement-name-key subelement-alias-map)]
    (update element subelement-key concat subelement-aliases)))

(defn update-collection-with-platform-aliases
  "Returns the collection with humanizer platform aliases added.
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
  [collection umm-spec-collection? humanizer-alias-map]
  (let [plat-key (if umm-spec-collection?
                   :Platforms
                   :platforms)
        plat-name-key (if umm-spec-collection?
                        :ShortName
                        :short-name)
        plat-alias-map (get humanizer-alias-map "platform")]
    (update-element-with-subelement-aliases
      collection plat-key plat-name-key plat-alias-map)))

(defn update-collection-with-tile-aliases
  "Returns the collection with humanizer tile aliases added"
  [collection umm-spec-collection? humanizer-alias-map]
  (let [tile-key (if umm-spec-collection?
                   :TilingIdentificationSystems
                   :two-d-coordinate-systems)
        tile-name-key (if umm-spec-collection?
                        :TilingIdentificationSystemName
                        :name)
        tile-alias-map (get humanizer-alias-map "tiling_system_name")]
     (update-element-with-subelement-aliases
       collection tile-key tile-name-key tile-alias-map)))

(defn update-collection-with-instrument-aliases
  "Returns the collection with humanizer instrument aliases added.
   Go through each platform and update the platform with all the instrument aliases"
  [collection umm-spec-collection? humanizer-alias-map]
  (let [plat-key (if umm-spec-collection?
                   :Platforms
                   :platforms)
        instr-key (if umm-spec-collection?
                    :Instruments
                    :instruments)
        instr-name-key (if umm-spec-collection?
                         :ShortName
                         :short-name)
        instr-alias-map (get humanizer-alias-map "instrument")
        plats (get collection plat-key)
        updated-plats (map #(update-element-with-subelement-aliases
                              % instr-key instr-name-key instr-alias-map) plats)]
    (assoc collection plat-key updated-plats)))

(defn update-collection-with-sensor-aliases
  "Returns the collection with humanizer instrument aliases added to sensors.
   Go through each platform and update the platform with all the instrument aliases for the child instruments."
  [collection umm-spec-collection? humanizer-alias-map]
  (let [plat-key (if umm-spec-collection?
                   :Platforms
                   :platforms)
        sensor-key (if umm-spec-collection?
                    :ComposedOf
                    :sensors)
        instr-key (if umm-spec-collection?
                    :Instruments
                    :instruments)
        sensor-name-key (if umm-spec-collection?
                         :ShortName
                         :short-name)
        instr-alias-map (get humanizer-alias-map "instrument")
        plats (get collection plat-key)
        updated-plats (map #(util/update-in-all
                             %
                             [instr-key]
                             update-element-with-subelement-aliases sensor-key sensor-name-key instr-alias-map)
                           plats)]
    (assoc collection plat-key updated-plats)))

(defn update-collection-with-aliases
  "Returns the collection with humanizer aliases added."
  [context collection umm-spec-collection?]
  (let [humanizer-alias-map (get-humanizer-alias-map context)]
    (-> collection
        (update-collection-with-platform-aliases umm-spec-collection? humanizer-alias-map)
        ;; instrument alias update needs to be after the platform as we want to add them
        ;; to all the platform instruments, including the alias platform's instruments.
        (update-collection-with-instrument-aliases umm-spec-collection? humanizer-alias-map)
        (update-collection-with-tile-aliases umm-spec-collection? humanizer-alias-map)
        (update-collection-with-sensor-aliases umm-spec-collection? humanizer-alias-map))))
