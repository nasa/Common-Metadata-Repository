(ns cmr.umm-spec.iso-utils
  "Contains functions for parsing and generating the ISO SMAP descriptiveKeywords. SMAP ISO
  collection science keywords, platforms and instruments are all represented as descriptiveKeywords.
  It would be better if the type element within the descriptiveKeywords could identify the type of
  the keywords. But currently it is always set to 'theme'. We will propose to get this changed,
  but in the mean time, we will have to parse the keyword string to determine the type of the keyword."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.xml :as cx]
            [cmr.umm-spec.models.common :as c]))

(def KEYWORD_SEPARATOR
  "Separator used to separator keyword into keyword fields"
  #" > ")

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
  [smap-keyword]
  (for [s (str/split smap-keyword KEYWORD_SEPARATOR)]
    (if (empty? s)
      nil
      s)))

(defn keyword-type
  "Returns a value indicating the category of the given keyword string."
  [smap-keyword]
  (let [category (first (parse-keyword-str smap-keyword))]
    (cond
      (science-keyword-categories category)    :science
      (re-matches #".* Instruments$" category) :instrument
      (platform-categories category)           :platform
      :else                                    :other)))

(defn parse-instrument
  "Converts the SMAP keyword string into an Instrument and returns it"
  [smap-keyword]
  (let [[_ _ _ _ short-name long-name] (parse-keyword-str smap-keyword)]
    {:ShortName short-name
     :LongName long-name}))

(defn platform
  "Returns the platform with the given keyword string and instruments"
  [instruments keyword-str]
  (let [[_ _ short-name long-name] (parse-keyword-str keyword-str)]
    {:ShortName short-name
     :LongName long-name
     :Type "Spacecraft"
     :Instruments instruments}))

(defn parse-platforms
  "Returns UMM PlatformType records with associated instruments from a seq of ISO SMAP keywords."
  [smap-keywords]
  (let [groups (group-by keyword-type smap-keywords)
        instruments (seq (map parse-instrument (:instrument groups)))]
    ;; There is no nested relationship between platform and instrument in SMAP ISO xml
    ;; So we put all instruments in each platform in the UMM
    (seq (map (partial platform instruments) (:platform groups)))))

(defmulti smap-keyword-str
  "Returns a SMAP keyword string for a given UMM record."
  (fn [record]
    (type record)))

(defmethod smap-keyword-str cmr.umm_spec.models.common.PlatformType
  [platform]
  (format "Aircraft > DUMMY > %s > %s"
          (:ShortName platform)
          ;; Because LongName is optional, we want an empty string instead of "null"
          ;; here to prevent problems when parsing.
          (str (:LongName platform))))

(defmethod smap-keyword-str cmr.umm_spec.models.common.InstrumentType
  [instrument]
  (format "Dummy Instruments > DUMMY > DUMMY > DUMMY > %s > %s"
          (:ShortName instrument)
          (str (:LongName instrument))))

(defn generate-id
  "Returns a 5 character random id to use as an ISO id"
  []
  (str "d" (java.util.UUID/randomUUID)))

