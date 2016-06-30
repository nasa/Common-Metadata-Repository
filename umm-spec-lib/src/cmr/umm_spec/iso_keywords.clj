(ns cmr.umm-spec.iso-keywords
  "Contains utility functions and constants for parsing and generating ISO-19115-2 and SMAP ISO
  keywords. Collection science keywords, platforms and instruments are all represented as
  descriptiveKeywords. It would be better if the type element within the descriptiveKeywords could
  identify the type of the keywords. But currently it is always set to 'theme'. We will propose to
  get this changed, but in the mean time, we will have to parse the keyword string to determine the
  type of the keyword."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.common.xml :as cx]
            [cmr.umm-spec.models.common :as c]
            [cmr.common.xml.parse :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]))

(def nil-science-keyword-field
  "String used in ISO19115-2 to indicate that a given science keyword field is not present."
  "NONE")

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
  "Returns a seq of individual components of an ISO-19115-2 or SMAP keyword string."
  [iso-keyword]
  (for [s (str/split iso-keyword iso/keyword-separator-split)]
    (if (empty? s)
      nil
      s)))

(defn keyword-type
  "Returns a value indicating the category of the given keyword string."
  [iso-keyword]
  (let [category (first (parse-keyword-str iso-keyword))]
    (cond
      (science-keyword-categories category)    :science
      (re-matches #".*Instruments$" category) :instrument
      (platform-categories category)           :platform
      :else                                    :other)))

(defn parse-instrument
  "Converts the SMAP keyword string into an Instrument and returns it"
  [iso-keyword]
  (let [[_ _ _ _ short-name long-name] (parse-keyword-str iso-keyword)]
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
  [iso-keywords]
  (let [groups (group-by keyword-type iso-keywords)
        instruments (seq (map parse-instrument (:instrument groups)))]
    ;; There is no nested relationship between platform and instrument in SMAP ISO xml
    ;; So we put all instruments in each platform in the UMM
    (seq (map (partial platform instruments) (:platform groups)))))

(defn descriptive-keywords
  "Returns the descriptive keywords values for the given parent element and keyword type"
  [md-data-id-el keyword-type]
  (values-at md-data-id-el
             (str "gmd:descriptiveKeywords/gmd:MD_Keywords"
                  (format "[gmd:type/gmd:MD_KeywordTypeCode/@codeListValue='%s']" keyword-type)
                  "/gmd:keyword/gco:CharacterString")))

(defn parse-science-keywords
  "Returns the science keywords parsed from the given xml document."
  [md-data-id-el]
  (for [sk (descriptive-keywords md-data-id-el "theme")
        :let [[category topic term variable-level-1 variable-level-2 variable-level-3
               detailed-variable] (map #(if (= nil-science-keyword-field %) nil %)
                                       (str/split sk iso/keyword-separator-split))]]
    {:Category category
     :Topic topic
     :Term term
     :VariableLevel1 variable-level-1
     :VariableLevel2 variable-level-2
     :VariableLevel3 variable-level-3
     :DetailedVariable detailed-variable}))

(defmulti smap-keyword-str
  "Returns a SMAP keyword string for a given UMM record."
  (fn [record]
    (type record)))

(defmethod smap-keyword-str cmr.umm_spec.models.common.PlatformType
  [platform]
  (format "Aircraft > Not provided > %s > %s"
          (:ShortName platform)
          ;; Because LongName is optional, we want an empty string instead of "null"
          ;; here to prevent problems when parsing.
          (str (:LongName platform))))

(defmethod smap-keyword-str cmr.umm_spec.models.common.InstrumentType
  [instrument]
  (format "Instruments > Not provided > Not provided  > Not provided  > %s > %s"
          (:ShortName instrument)
          (str (:LongName instrument))))

(defn science-keyword->iso-keyword-string
  "Returns an ISO science keyword string from the given science keyword."
  [science-keyword]
  (let [{category :Category
         topic :Topic
         term :Term
         variable-level-1 :VariableLevel1
         variable-level-2 :VariableLevel2
         variable-level-3 :VariableLevel3
         detailed-variable :DetailedVariable} science-keyword]
    (->> [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]
         (map #(or % nil-science-keyword-field))
         (str/join iso/keyword-separator-join))))

(defn- generate-descriptive-keywords
  "Returns the content generator instructions for the given descriptive keywords."
  [keyword-type keywords code-lists]
  (when (seq keywords)
    [:gmd:descriptiveKeywords
     [:gmd:MD_Keywords
      (for [keyword keywords]
        [:gmd:keyword [:gco:CharacterString keyword]])
      (when keyword-type
        [:gmd:type
         [:gmd:MD_KeywordTypeCode
          {:codeList (str code-lists "#MD_KeywordTypeCode")
           :codeListValue keyword-type} keyword-type]])
      [:gmd:thesaurusName {:gco:nilReason "unknown"}]]]))

(defn generate-iso19115-descriptive-keywords
  "Returns the content generator instructions ISO19115-2 descriptive keywords."
  [keyword-type keywords]
  (generate-descriptive-keywords keyword-type keywords (:ngdc iso/code-lists)))

(defn generate-iso-smap-descriptive-keywords
  "Returns the content generator instructions for ISO-SMAP descriptive keywords."
  [keyword-type keywords]
  (generate-descriptive-keywords keyword-type keywords (:iso iso/code-lists)))
