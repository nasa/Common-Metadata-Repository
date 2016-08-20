(ns cmr.umm.dif10.collection.platform
  "Functions to parse and generate DIF10 Platform elements"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.dif.dif-core :as dif]
            [cmr.umm.dif10.collection.instrument :as inst]
            [cmr.umm.dif10.collection.characteristic :as char]))

(def platform-types
  "The set of values that DIF 10 defines for platform types as enumerations in its schema"
  #{c/not-provided
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
  (c/map->Platform
    {:short-name (cx/string-at-path platform-elem [:Short_Name])
     :long-name (cx/string-at-path platform-elem [:Long_Name])
     :type (cx/string-at-path platform-elem [:Type])
     :instruments (inst/xml-elem->Instruments platform-elem)
     :characteristics (char/xml-elem->Characteristics platform-elem)}))

(defn xml-elem->Platforms
  [collection-element]
  (seq (map xml-elem->Platform
            (cx/elements-at-path
              collection-element
              [:Platform]))))

(defn generate-platforms
  [platforms]
  (if (seq platforms)
    (for [{:keys [short-name long-name type instruments characteristics]} platforms]
      (x/element :Platform {}
                 (x/element :Type {} (or (platform-types type) c/not-provided))
                 (x/element :Short_Name {} short-name)
                 (x/element :Long_Name {} long-name)
                 (char/generate-characteristics characteristics)
                 (inst/generate-instruments instruments)))
    ;; Added since Platforms is a required field in DIF10. CMRIN-77 & CMRIN-79
    (x/element :Platform {}
               (x/element :Type {} c/not-provided)
               (x/element :Short_Name {} c/not-provided)
               (inst/generate-instruments []))))
