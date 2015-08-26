(ns cmr.umm-spec.iso-smap-utils
  "Contains functions for parsing and generating the ISO SMAP descriptiveKeywords. SMAP ISO
  collection science keywords, platforms and instruments are all represented as descriptiveKeywords.
  It would be better if the type element within the descriptiveKeywords could identify the type of
  the keywords. But currently it is always set to 'theme'. We will propose to get this changed,
  but in the mean time, we will have to parse the keyword string to determine the type of the keyword."
  (:require [clojure.data.xml :as x]
            [clojure.string :as s]
            [cmr.common.xml :as cx]
            [cmr.umm-spec.models.common :as c]))

(def platform-categories
  "Category keywords that identifies a descriptive keyword as a platform.
  We are doing this because GCMD currently is not setting the proper value in the type element
  of descriptiveKeywords to identify its type. We should use the type element to identify
  the type of the keyword once GCMD fixes that."
  #{"Aircraft"
    "Balloons/Rockets"
    "Earth Observation Satellites"
    "In Situ Land-based Platforms"
    "In Situ Ocean-based Platforms"
    "Interplanetary Spacecraft"
    "Maps/Charts/Photographs"
    "Models/Analyses"
    "Navigation Platforms"
    "Solar/Space Observation Satellites"
    "Space Stations/Manned Spacecraft"})

(def science-keyword-categories
  #{"EARTH SCIENCE" "EARTH SCIENCE SERVICES"})

(defn parse-keyword-str
  "Returns a seq of individual components of an ISO SMAP keyword string."
  [keyword-str]
  (->> (.split keyword-str ">")
       (map s/trim)
       (remove empty?)))

(defn keyword-type
  "Returns a value indicating the category of the given keyword string."
  [keyword-str]
  (let [category (first (parse-keyword-str keyword-str))]
    (cond
      (science-keyword-categories category)    :science
      (re-matches #".* Instruments$" category) :instrument
      (platform-categories category)           :platform
      :else                                    :other)))

(defn parse-platform
  "Returns a UMM PlatformType record from the given keyword string."
  [keyword-str]
  (let [[_ _ short-name long-name] (parse-keyword-str keyword-str)]
    (c/map->PlatformType
      {:ShortName short-name
       :LongName long-name
       :Type "Spacecraft"})))
