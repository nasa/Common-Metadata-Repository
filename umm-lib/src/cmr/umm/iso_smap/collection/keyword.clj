(ns cmr.umm.iso-smap.collection.keyword
  "Contains functions for parsing and generating the ISO SMAP descriptiveKeywords. SMAP ISO
  collection science keywords, platforms and instruments are all represented as descriptiveKeywords.
  It would be better if the type element within the descriptiveKeywords could identify the type of
  the keywords. But currently it is always set to 'theme'. We will propose to get this changed,
  but in the mean time, we will have to parse the keyword string to determine the type of the keyword."
  (:require [clojure.data.xml :as x]
            [clojure.string :as s]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-smap.helper :as h])
  (:import cmr.umm.umm_collection.Platform
           cmr.umm.umm_collection.Instrument
           cmr.umm.umm_collection.ScienceKeyword))

(def KEYWORD_SEPARATOR
  "Separator used to separator keyword into keyword fields"
  #">")

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

(defn- parse-keyword-str
  "Parse the keyword string into a vector of keyword fields"
  [keyword-str]
  (map (comp #(if (empty? %) nil %) s/trim)
       (s/split keyword-str KEYWORD_SEPARATOR)))

(defn- keyword->keyword-type
  "Returns the keyword type for the given keyword string"
  [keyword-str]
  (let [category (first (parse-keyword-str keyword-str))]
    (cond
      (science-keyword-categories category) :science
      (re-matches #".*Instruments$" category) :instrument
      (platform-categories category) :platform
      :else :other)))

(defn xml-elem->keywords
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
       :long-name long-name})))

(defn platform
  "Returns the platform with the given keyword string and instruments"
  [instruments keyword-str]
  (let [[_ _ short-name long-name] (parse-keyword-str keyword-str)]
    (c/map->Platform
      {:short-name short-name
       :long-name long-name
       :type "Spacecraft"
       :instruments instruments})))

(defn keywords->Platforms
  "Returns the UMM Platforms from the given keywords parsed from the xml"
  [keywords]
  (let [{instruments :instrument platforms :platform} keywords
        instruments (seq (map string->instrument instruments))]
    ;; There is no nested relationship between platform and instrument in SMAP ISO xml
    ;; So we put all instruments in each platform in the UMM
    (seq (map (partial platform instruments) platforms))))

(defn- string->ScienceKeyword
  "Converts the keyword string into a ScienceKeyword and returns it"
  [keyword-str]
  (let [[category topic term variable-level-1 variable-level-2
         variable-level-3 detailed-variable] (parse-keyword-str keyword-str)]
    (c/map->ScienceKeyword
      {:category category
       :topic topic
       :term term
       :variable-level-1 variable-level-1
       :variable-level-2 variable-level-2
       :variable-level-3 variable-level-3
       :detailed-variable detailed-variable})))

(defn keywords->ScienceKeywords
  "Returns the UMM ScienceKeywords from the given keywords parsed from the xml"
  [keywords]
  (let [{science-keywords :science} keywords]
    (seq (map string->ScienceKeyword science-keywords))))

(defmulti generate-keyword
  "Generate the keyword element"
  (fn [kw]
    (type kw)))

(defmethod generate-keyword ScienceKeyword
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2
                variable-level-3 detailed-variable]} science-keyword
        keywords (map str [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable])
        science-keyword-str (apply (partial format "%s > %s > %s > %s > %s > %s > %s") keywords)]
    (h/iso-string-element :gmd:keyword science-keyword-str)))

(defmethod generate-keyword Platform
  [platform]
  (let [{:keys [short-name long-name]} platform
        ;; There is a disconnect between UMM platform type and the SMAP ISO platform keyword category
        ;; We will just hardcode it to be "Aircraft" for now.
        platform-str (format "Aircraft > %s > %s > %s" c/not-provided short-name (str long-name))]
    (h/iso-string-element :gmd:keyword platform-str)))

(defmethod generate-keyword Instrument
  [instrument]
  (let [{:keys [short-name long-name]} instrument
        instrument-str (format "Instruments > %s > %s > %s > %s > %s"
                               c/not-provided  c/not-provided c/not-provided short-name (str long-name))]
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
                            (x/element :gmd:date {:gco:nilReason c/not-provided}))))))

(defn generate-keywords
  [keywords]
  (when (seq keywords)
    (generate-descriptive-keywords keywords)))
