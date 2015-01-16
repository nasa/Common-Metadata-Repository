(ns cmr.umm.iso-smap.collection.keyword
  "Contains functions for parsing and generating the ISO SMAP descriptiveKeywords"
  (:require [clojure.data.xml :as x]
            [clojure.string :as s]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.iso-smap.helper :as h])
  (:import cmr.umm.collection.Platform
           cmr.umm.collection.Instrument))

(def KEYWORD_SEPARATOR
  "Separator used to separator keyword into keyword fields"
  #">")

(def PLATFORM_CATEGORIES
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

(defn- parse-keyword-str
  "Parse the keyword string into a vector of keyword fields"
  [keyword-str]
  (map s/trim (s/split keyword-str KEYWORD_SEPARATOR)))

(defn- keyword->keyword-type
  "Returns the keyword type for the given keyword string"
  [keyword-str]
  (let [category (first (parse-keyword-str keyword-str))]
    (cond
      (= "EARTH SCIENCE" category) :science
      (re-matches #".* Instruments$" category) :instrument
      (some PLATFORM_CATEGORIES [category]) :platform
      :else :other)))

(defn- xml-elem->keywords
  "Returns the map of keywords (science, platfrom and intrument) from a parsed XML structure"
  [xml-struct]
  (let [keywords (cx/strings-at-path
                   xml-struct
                   [:seriesMetadata :MI_Metadata :identificationInfo
                    :MD_DataIdentification :descriptiveKeywords :MD_Keywords :keyword :CharacterString])]
    (group-by keyword->keyword-type keywords)))

(defn- string->instrument
  "Converts the keyword string into an Instrument and returns it"
  [keyword-str]
  (let [[_ _ _ _ short-name long-name] (parse-keyword-str keyword-str)]
    (c/map->Instrument
      {:short-name short-name
       :long-name (if (empty? long-name) nil long-name)})))

(defn platform
  "Returns the platform with the given keyword string and instruments"
  [instruments keyword-str]
  (let [[_ _ short-name long-name] (parse-keyword-str keyword-str)]
    (c/map->Platform
      {:short-name short-name
       :long-name (if (empty? long-name) nil long-name)
       :type "Spacecraft"
       :instruments instruments})))

(defn xml-elem->Platforms
  [xml-struct]
  (let [{instruments :instrument platforms :platform} (xml-elem->keywords xml-struct)
        instruments (seq (map string->instrument instruments))]
    ;; There is no nested relationship between platform and instrument in SMAP ISO xml
    ;; So we put all instruments in each platform in the UMM
    (seq (map (partial platform instruments) platforms))))

(defmulti generate-keyword
  "Generate the keyword element"
  (fn [kw]
    (type kw)))

(defmethod generate-keyword Platform
  [platform]
  (let [{:keys [short-name long-name]} platform
        long-name (if long-name long-name "")
        ;; There is a disconnect between UMM platform type and the SMAP ISO platform keyword category
        ;; We will just hardcode it to be "Aircraft" for now.
        platform-str (format "Aircraft > DUMMY > %s > %s" short-name long-name)]
    (h/iso-string-element :gmd:keyword platform-str)))

(defmethod generate-keyword Instrument
  [instrument]
  (let [{:keys [short-name long-name]} instrument
        long-name (if long-name long-name "")
        instrument-str (format "Dummy Instruments > DUMMY > DUMMY > DUMMY > %s > %s"
                               short-name long-name)]
    (h/iso-string-element :gmd:keyword instrument-str)))

(defn- generate-descriptive-keywords
  [keywords]
  (x/element
    :gmd:descriptiveKeywords {}
    (x/element
      :gmd:MD_Keywords {}
      (for [kw keywords]
        (generate-keyword kw))
      (x/element
        :gmd:type {}
        (x/element
          :gmd:MD_KeywordTypeCode
          {:codeList "http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_KeywordTypeCode"
           :codeListValue "theme"}
          "theme"))
      (x/element :gmd:thesaurusName {}
                 (x/element :gmd:CI_Citation {}
                            (x/element :gmd:title {}
                                       (x/element :gco:CharacterString {}
                                                  "NASA/GCMD Earth Science Keywords"))
                            (x/element :gmd:date {:gco:nilReason "unknown"}))))))

(defn generate-instruments
  [instruments]
  (when (seq instruments)
    (generate-descriptive-keywords instruments)))

(defn generate-platforms
  [platforms]
  (when (seq platforms)
    (generate-descriptive-keywords platforms)))