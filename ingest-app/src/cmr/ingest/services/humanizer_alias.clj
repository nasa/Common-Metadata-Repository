(ns cmr.ingest.services.humanizer-alias
  "Stores the latest humanizer platform alias map in a cache.
   The keys are the platform shortnames and the value for each key is
   a list of platform alias shortnames for that given platform shortname."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common-app.data.humanizer-alias-cache :as cmn-humanizer-alias-cache]))

(defn- get-field-aliases
  "Returns field aliases for a given element's fields, field-name-key and a field-alias-map.
   Note: if the field-name of a field alias already exists in the element's fields, this
   alias won't be added to the field-aliases."
  [fields field-name-key field-alias-map]
  (let [field-names-set (set (map field-name-key fields))
        field-aliases
         (for [coll-field fields
               :let [coll-field-name (get coll-field field-name-key)
                     aliases-set (set (get field-alias-map (string/upper-case coll-field-name)))]
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
  [collection umm-spec-collection? plat-alias-map]
  (let [plat-key (if umm-spec-collection?
                   :Platforms
                   :platforms)
        plat-name-key (if umm-spec-collection?
                        :ShortName
                        :short-name)]
    (update-element-with-subelement-aliases
      collection plat-key plat-name-key plat-alias-map)))

(defn update-collection-with-tile-aliases
  "Returns the collection with humanizer tile aliases added"
  [collection umm-spec-collection? tile-alias-map]
  (let [tile-key (if umm-spec-collection?
                   :TilingIdentificationSystems
                   :two-d-coordinate-systems)
        tile-name-key (if umm-spec-collection?
                        :TilingIdentificationSystemName
                        :name)]
     (update-element-with-subelement-aliases
       collection tile-key tile-name-key tile-alias-map)))

(defn update-collection-with-instrument-aliases
  "Returns the collection with humanizer instrument aliases added.
   Go through each platform and update the platform with all the instrument aliases"
  [collection umm-spec-collection? instr-alias-map]
  (let [plat-key (if umm-spec-collection?
                   :Platforms
                   :platforms)
        instr-key (if umm-spec-collection?
                    :Instruments
                    :instruments)
        instr-name-key (if umm-spec-collection?
                         :ShortName
                         :short-name)
        plats (get collection plat-key)
        updated-plats (map #(update-element-with-subelement-aliases
                              % instr-key instr-name-key instr-alias-map) plats)]
    (assoc collection plat-key updated-plats)))

(defn update-collection-with-sensor-aliases
  "Returns the collection with humanizer instrument aliases added to sensors.
   Go through each platform and update the platform with all the instrument aliases for the child instruments."
  [collection umm-spec-collection? instr-alias-map]
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
  (let [platform-humanizer-alias-map (cmn-humanizer-alias-cache/get-non-humanized-source-to-aliases-map context "platform")
        instrument-humanizer-alias-map (cmn-humanizer-alias-cache/get-non-humanized-source-to-aliases-map context "instrument")
        tile-humanizer-alias-map (cmn-humanizer-alias-cache/get-non-humanized-source-to-aliases-map context "tiling_system_name")]
    (-> collection
        (update-collection-with-platform-aliases umm-spec-collection? platform-humanizer-alias-map)
        ;; instrument alias update needs to be after the platform as we want to add them
        ;; to all the platform instruments, including the alias platform's instruments.
        (update-collection-with-instrument-aliases umm-spec-collection? instrument-humanizer-alias-map)
        (update-collection-with-tile-aliases umm-spec-collection? tile-humanizer-alias-map)
        (update-collection-with-sensor-aliases umm-spec-collection? instrument-humanizer-alias-map))))
