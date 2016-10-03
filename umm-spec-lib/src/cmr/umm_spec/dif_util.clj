(ns cmr.umm-spec.dif-util
  "Contains common definitions and functions for DIF9 and DIF10 metadata parsing and generation."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.common.util :as common-util]
   [cmr.umm-spec.util :as util]))

;; For now, we assume DIF9 and DIF10 contain the same IDN_Nodes structures
;; after confirming with the system engineer people - even though some of DIF10 files
;; contain different structures. 
;; Will need to revisit this after we get the new version of the DIF10 schema
(defn parse-idn-node
  "Returns DirectoryNames for the provided DIF doc"
  [doc]
  (when-let [dnames (seq (select doc "/DIF/IDN_Node"))]
    (for [dirname dnames]
      {:ShortName (value-of dirname "Short_Name")
       :LongName (value-of dirname "Long_Name")})))

(defn generate-idn-nodes
  "Returns IDN_Nodes for the provided UMM-C collection record"
  [c]
  (when-let [dnames (:DirectoryNames c)]
    (for [{:keys [ShortName LongName]} dnames]
      [:IDN_Node
       [:Short_Name ShortName]
       [:Long_Name LongName]])))

(def iso-639-2->dif10-dataset-language
  "Mapping from ISO 639-2 to the enumeration supported for dataset languages in DIF10."
  {"eng" "English"
   "afr" "Afrikaans"
   "ara" "Arabic"
   "bos" "Bosnian"
   "bul" "Bulgarian"
   "chi" "Chinese"
   "zho" "Chinese"
   "hrv" "Croatian"
   "cze" "Czech"
   "ces" "Czech"
   "dan" "Danish"
   "dum" "Dutch"
   "dut" "Dutch"
   "nld" "Dutch"
   "est" "Estonian"
   "fin" "Finnish"
   "fre" "French"
   "fra" "French"
   "gem" "German"
   "ger" "German"
   "deu" "German"
   "gmh" "German"
   "goh" "German"
   "gsw" "German"
   "nds" "German"
   "heb" "Hebrew"
   "hun" "Hungarian"
   "ind" "Indonesian"
   "ita" "Italian"
   "jpn" "Japanese"
   "kor" "Korean"
   "lav" "Latvian"
   "lit" "Lithuanian"
   "nno" "Norwegian"
   "nob" "Norwegian"
   "nor" "Norwegian"
   "pol" "Polish"
   "por" "Portuguese"
   "rum" "Romanian"
   "ron" "Romanian"
   "rup" "Romanian"
   "rus" "Russian"
   "slo" "Slovak"
   "slk" "Slovak"
   "spa" "Spanish"
   "ukr" "Ukrainian"
   "vie" "Vietnamese"})

(def dif10-dataset-language->iso-639-2
  "Mapping from the DIF10 enumeration dataset languages to ISO 639-2 language code."
  (set/map-invert iso-639-2->dif10-dataset-language))

(def dif-iso-topic-category->umm-iso-topic-category
  "DIF ISOTopicCategory to UMM ISOTopicCategory mapping. Some of the DIF ISOTopicCategory are made
  up based on intuition and may not be correct. Fix them when identified."
  {"CLIMATOLOGY/METEOROLOGY/ATMOSPHERE" "climatologyMeteorologyAtmosphere"
   "ENVIRONMENT" "environment"
   "FARMING" "farming"
   "BIOTA" "biota"
   "BOUNDARIES" "boundaries"
   "ECONOMY" "economy"
   "ELEVATION" "elevation"
   "GEOSCIENTIFIC INFORMATION" "geoscientificInformation"
   "HEALTH" "health"
   "IMAGERY/BASE MAPS/EARTH COVER" "imageryBaseMapsEarthCover"
   "INTELLIGENCE/MILITARY" "intelligenceMilitary"
   "INLAND WATERS" "inlandWaters"
   "LOCATION" "location"
   "OCEANS" "oceans"
   "PLANNING CADASTRE" "planningCadastre"
   "SOCIETY" "society"
   "STRUCTURE" "structure"
   "TRANSPORTATION" "transportation"
   "UTILITIES/COMMUNICATION" "utilitiesCommunication"})

(def umm-iso-topic-category->dif-iso-topic-category
  "UMM ISOTopicCategory to DIF ISOTopicCategory mapping."
  (set/map-invert dif-iso-topic-category->umm-iso-topic-category))

(def ^:private dif10-dataset-languages
  "Set of Dataset_Languages supported in DIF10"
  (set (vals iso-639-2->dif10-dataset-language)))

(defn- umm-langage->dif-language
  "Return DIF9/DIF10 dataset language for the given umm DataLanguage.
  Currenlty, the UMM JSON schema does mandate the language as an enum, so we try our best to match
  the possible arbitrary string to a defined DIF10 language enum."
  [language]
  (let [language (util/capitalize-words language)]
    (if (dif10-dataset-languages language)
        language
        (get iso-639-2->dif10-dataset-language language "English"))))

(defn dif-language->umm-langage
  "Return UMM DataLanguage for the given DIF9/DIF10 dataset language."
  [language]
  (when-let [language (util/capitalize-words language)]
    (get dif10-dataset-language->iso-639-2 language "eng")))

(defn generate-dataset-language
  "Return DIF9/DIF10 dataset language generation instruction with the given element key
  and UMM DataLanguage"
  [element-key data-language]
  (when data-language
    [element-key (umm-langage->dif-language data-language)]))

(defn parse-access-constraints
  "If both Value and Description are nil, return nil.
  Otherwise, if Description is nil, assoc it with u/not-provided"
  [doc sanitize?]
  (let [access-constraints-record
        {:Description (util/truncate
                       (value-of doc "/DIF/Access_Constraints")
                       util/ACCESSCONSTRAINTS_DESCRIPTION_MAX
                       sanitize?)
         :Value (value-of doc "/DIF/Extended_Metadata/Metadata[Name='Restriction']/Value")}]
    (when (seq (common-util/remove-nil-keys access-constraints-record))
      (update access-constraints-record :Description #(util/with-default % sanitize?)))))

(defn- parse-iso-topic-category
  "Returns the parsed UMM iso topic category for the given dif iso topic category"
  [dif-itc sanitize?]
  (if-let [umm-itc (dif-iso-topic-category->umm-iso-topic-category dif-itc)]
    umm-itc
    ;; When the DIF iso topic category is not in the defined mappings,
    ;; if sanitize? is true, we return nil so that the invalid iso topic category will be dropped
    ;; if sanitize? is false, we return the original value and it will fail the UMM JSON schema validation
    (when-not sanitize?
      dif-itc)))

(defn parse-iso-topic-categories
  "Returns parsed UMM IsoTopicCategories"
  [doc sanitize?]
  (let [iso-topic-categories (values-at doc "DIF/ISO_Topic_Category")]
    (keep #(parse-iso-topic-category % sanitize?) iso-topic-categories)))
