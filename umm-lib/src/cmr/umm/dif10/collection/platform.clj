(ns cmr.umm.dif10.collection.platform
  "Functions to parse and generate DIF10 Platform elements"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.dif10.collection.instrument :as inst]
            [cmr.umm.dif10.collection.characteristic :as char]))

(def PLATFORM_TYPES
  #{"Not provided"
    "Aircraft"
    "Balloons/Rockets"
    "Earth Observation Satellites"
    "In Situ Land-based Platforms"
    "In Sit Ocean-based Platforms"
    "Interplanetary Spacecraft"
    "Maps/Charts/Photographs"
    "Models/Analyses"
    "Navigation Platforms"
    "Solar/Space Observation Satellites"
    "Space Stations/Manned Spacecraft"})

(defn xml-elem->Platform
  [platform-elem]
  (let [short-name (cx/string-at-path platform-elem [:Short_Name])
        long-name (cx/string-at-path platform-elem [:Long_Name])
        type (cx/string-at-path platform-elem [:Type])
        characteristics (char/xml-elem->Characteristics platform-elem)
        instruments (inst/xml-elem->Instruments platform-elem)]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       :type type
       :instrument instruments
       :characteristics characteristics})))

(defn xml-elem->Platforms
  [collection-element]
  (seq (map xml-elem->Platform
            (cx/elements-at-path
              collection-element
              [:Platform]))))

(defn generate-platforms
  [platforms]
  (if (seq platforms)
    (for [platform platforms]
      (let [{:keys [short-name long-name type instruments characteristics]} platform]
        (x/element :Platform {}
                   (x/element :Type {} (or (PLATFORM_TYPES type) "Not provided"))
                   (x/element :Short_Name {} short-name)
                   (x/element :Long_Name {} long-name)
                   (char/generate-characteristics characteristics)
                   (inst/generate-instruments instruments))))
    ;;Added since Platforms is a required field in DIF10
    (x/element :Platform {}
               (x/element :Type {} "Not provided")
               (x/element :Short_Name {} "Not provided")
               (inst/generate-instruments []))))