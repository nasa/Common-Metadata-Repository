(ns cmr.umm-spec.dif-util
  "Contains common definitions and functions for DIF9 and DIF10 metadata parsing and generation."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.common.util :as common-util]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as util]))

(def dif-online-resource-name
 "Reference Online Resource")

(def dif-online-resource-description
 "Reference Online Resource")

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

(def dif-url-content-type->umm-url-types
 "Mapping from the dif URL Content Type type and subtype to UMM URLContentType, Type, and Subtype
  Pair of ['Type' 'Subtype'] -> {:URLContentType 'X' :Type 'Y' :Subtype 'Z'}
  Note UMM Subtype is not required so there may not be a subtype"
 {["GET DATA" nil] {:URLContentType "DistributionURL" :Type "GET DATA"}
  ["GET DATA" "ALTERNATE ACCESS"] {:URLContentType "DistributionURL" :Type "GET DATA"}
  ["GET DATA" "DATACAST URL"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"}
  ["GET DATA" "EARTHDATA SEARCH"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EARTHDATA SEARCH"}
  ["GET DATA" "ECHO"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ECHO"}
  ["GET DATA" "EDG"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EDG"}
  ["GET DATA" "EOSDIS DATA POOL"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"}
  ["GET DATA" "GDS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"}
  ["GET DATA" "GIOVANNI"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"}
  ["GET DATA" "KML"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "KML"}
  ["GET DATA" "LAADS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"}
  ["GET DATA" "LANCE"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"}
  ["GET DATA" "LAS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAS"}
  ["GET DATA" "MIRADOR"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"}
  ["GET DATA" "MODAPS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"}
  ["GET DATA" "NOAA CLASS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"}
  ["GET DATA" "NOMADS"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "NOMADS"}
  ["GET DATA" "ON-LINE ARCHIVE"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ON-LINE ARCHIVE"}
  ["GET DATA" "OPENDAP DATA"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA"}
  ["GET DATA" "OPENDAP DATA (DODS)"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA (DODS)"}
  ["GET DATA" "OPENDAP DIRECTORY (DODS)"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DIRECTORY (DODS)"}
  ["GET DATA" "REVERB"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "REVERB"}
  ["GET DATA" "SSW"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SSW"}
  ["GET DATA" "SUBSETTER"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SUBSETTER"}
  ["GET DATA" "THREDDS CATALOG"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREADS CATALOG"}
  ["GET DATA" "THREDDS DATA"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREADS DATA"}
  ["GET DATA" "THREDDS DIRECTORY"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DIRECTORY"}
  ["GET RELATED DATA SET METADATA" nil] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "DIF"}
  ["GET RELATED SERVICE METADATA" nil] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SERF"}
  ["GET RELATED VISUALIZATION" nil] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  ["GET RELATED VISUALIZATION" "GIOVANNI"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"}
  ["GET RELATED VISUALIZATION" "WORLDVIEW"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIBS"}
  ["GET SERVICE" nil] {:URLContentType "DistributionURL" :Type "GET SERVICE"}
  ["GET SERVICE" "ACCESS MAP VIEWER"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MAP VIEWER"}
  ["GET SERVICE" "ACCESS MOBILE APP"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MOBILE APP"}
  ["GET SERVICE" "ACCESS WEB SERVICE"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS WEB SERVICE"}
  ["GET SERVICE" "DATA LIST"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "DATA LIST"}
  ["GET SERVICE" "GET MAP SERVICE"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "MAP SERVICE"}
  ["GET SERVICE" "GET SOFTWARE PACKAGE"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SOFTWARE PACKAGE"}
  ["GET SERVICE" "GET WEB COVERAGE SERVICE (WCS)"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB COVERAGE SERVICE (WCS)"}
  ["GET SERVICE" "GET WEB FEATURE SERVICE (WFS)"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB FEATURE SERVICE (WFS)"}
  ["GET SERVICE" "GET WEB MAP FOR TIME SERIES"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP FOR TIME SERIES"}
  ["GET SERVICE" "GET WEB MAP SERVICE (WMS)"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP SERVICE (WMS)"}
  ["GET SERVICE" "GET WORKFLOW (SERVICE CHAIN)"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WORKFLOW (SERVICE CHAIN)"}
  ["GET SERVICE" "OpenSearch"] {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OpenSearch"}
  ["VIEW DATA SET LANDING PAGE" nil] {:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"}
  ["VIEW EXTENDED METADATA" nil] {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  ["VIEW IMAGES" nil] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  ["VIEW IMAGES" "BROWSE SAMPLE"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  ["VIEW PROFESSIONAL HOME PAGE" nil] {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"}
  ["VIEW PROJECT HOME PAGE" nil] {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"}
  ["VIEW RELATED INFORMATION" nil] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  ["VIEW RELATED INFORMATION" "ALGORITHM DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"}
  ["VIEW RELATED INFORMATION" "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"}
  ["VIEW RELATED INFORMATION" "ANOMALIES"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "HOW-TO"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"}
  ["VIEW RELATED INFORMATION" "IMPORTANT NOTICE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "PI DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "PRODUCT HISTORY"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"}
  ["VIEW RELATED INFORMATION" "PUBLICATIONS"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"}
  ["VIEW RELATED INFORMATION" "USER'S GUIDE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"}
  ["VIEW RELATED INFORMATION" "VIEW MICRO ARTICLE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}})

(def umm-url-type->dif-umm-content-type
 "Map a combination of UMM URLContentType, Type, and Subtype (optional) to a dif url content type
 type and subtype. This is not dif->umm list flipped."
 {{:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"} ["VIEW DATA SET LANDING PAGE" nil]
  {:URLContentType "CollectionURL" :Type "DOI"} ["VIEW DATA SET LANDING PAGE" nil]
  {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"} ["VIEW EXTENDED METADATA" nil]
  {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"} ["VIEW PROFESSIONAL HOME PAGE" nil]
  {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"} ["VIEW PROJECT HOME PAGE" nil]
  {:URLContentType "DistributionURL" :Type "GET DATA"} ["GET DATA" nil]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"} ["GET DATA" "DATACAST URL"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EARTHDATA SEARCH"} ["GET DATA" "EARTHDATA SEARCH"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ECHO"} ["GET DATA" "ECHO"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EDG"} ["GET DATA" "EDG"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"} ["GET DATA" "EOSDIS DATA POOL"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GDS"} ["GET DATA" "GDS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"} ["GET DATA" "GIOVANNI"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "KML"} ["GET DATA" "KML"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"} ["GET DATA" "LAADS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"} ["GET DATA" "LANCE"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAS"} ["GET DATA" "LAS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"} ["GET DATA" "MIRADOR"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"} ["GET DATA" "MODAPS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"} ["GET DATA" "NOAA CLASS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOMADS"} ["GET DATA" "NOMADS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ON-LINE ARCHIVE"} ["GET DATA" "ON-LINE ARCHIVE"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "REVERB"} ["GET DATA" "REVERB"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE"} ["GET SERVICE" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MAP VIEWER"} ["GET SERVICE" "ACCESS MAP VIEWER"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MOBILE APP"} ["GET SERVICE" "ACCESS MOBILE APP"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS WEB SERVICE"} ["GET SERVICE" "ACCESS WEB SERVICE"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "DIF"} ["GET RELATED DATA SET METADATA" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "GET MAP SERVICE"} ["GET SERVICE" "GET MAP SERVICE"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "NOMADS"} ["GET DATA" "NOMADS"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA"} ["GET DATA" "OPENDAP DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA (DODS)"} ["GET DATA" "OPENDAP DATA (DODS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DIRECTORY (DODS)"} ["GET DATA" "OPENDAP DIRECTORY (DODS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OpenSearch"} ["GET SERVICE" "OpenSearch"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SERF"} ["GET RELATED SERVICE METADATA" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "GET SOFTWARE PACKAGE"} ["GET SERVICE" "GET SOFTWARE PACKAGE"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SSW"} ["GET DATA" "SSW"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SUBSETTER"} ["GET DATA" "SUBSETTER"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS CATALOG"} ["GET DATA" "THREDDS CATALOG"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DATA"} ["GET DATA" "THREDDS DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DIRECTORY"} ["GET DATA" "THREDDS DIRECTORY"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "GET WEB COVERAGE SERVICE (WCS)"} ["GET SERVICE" "GET WEB COVERAGE SERVICE (WCS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "GET WEB FEATURE SERVICE (WFS)"} ["GET SERVICE" "GET WEB FEATURE SERVICE (WFS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "GET WEB MAP FOR TIME SERIES"} ["GET SERVICE" "GET WEB MAP FOR TIME SERIES"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "GET WEB MAP SERVICE (WMS)"} ["GET SERVICE" "GET WEB MAP SERVICE (WMS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "GET WORKFLOW (SERVICE CHAIN)"} ["GET SERVICE" "GET WORKFLOW (SERVICE CHAIN)"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"} ["VIEW RELATED INFORMATION" nil]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"} ["VIEW RELATED INFORMATION" "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CALIBRATION DATA DOCUMENTATION"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA USAGE"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"} ["VIEW RELATED INFORMATION" "HOW-TO"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"} ["VIEW RELATED INFORMATION" "PI DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION VERSION HISTORY"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"} ["VIEW RELATED INFORMATION" "PRODUCT HISTORY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"} ["VIEW RELATED INFORMATION" "PUBLICATIONS"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "RADIOMETRIC AND GEOMETRIC CALIBRATION METHODS"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "RECIPE"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"} ["VIEW RELATED INFORMATION" "USER'S GUIDE"]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"} ["GET RELATED VISUALIZATION" nil]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"} ["GET RELATED VISUALIZATION" "GIOVANNI"]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIBS"} ["GET RELATED VISUALIZATION" "WORLDVIEW"]})

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

(defn umm-language->dif-language
  "Return DIF9/DIF10 dataset language for the given umm DataLanguage.
  Currenlty, the UMM JSON schema does mandate the language as an enum, so we try our best to match
  the possible arbitrary string to a defined DIF10 language enum."
  [language]
  (let [language (util/capitalize-words language)]
    (if (dif10-dataset-languages language)
        language
        (get iso-639-2->dif10-dataset-language language "English"))))

(defn dif-language->umm-language
  "Return UMM DataLanguage for the given DIF9/DIF10 dataset language."
  [language]
  (when-let [language (util/capitalize-words language)]
    (get dif10-dataset-language->iso-639-2 language "eng")))

(defn generate-dataset-language
  "Return DIF9/DIF10 dataset language generation instruction with the given element key
  and UMM DataLanguage"
  [element-key data-language]
  (when data-language
    [element-key (umm-language->dif-language data-language)]))

(defn parse-access-constraints
  "If both Value and Description are nil, return nil.
  Otherwise, if Description is nil, assoc it with u/not-provided"
  [doc sanitize?]
  (let [value (value-of doc "/DIF/Extended_Metadata/Metadata[Name='Restriction']/Value")
        access-constraints-record
        {:Description (util/truncate
                       (value-of doc "/DIF/Access_Constraints")
                       util/ACCESSCONSTRAINTS_DESCRIPTION_MAX
                       sanitize?)
         :Value (when value
                 (Double/parseDouble value))}]
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

(defn parse-publication-reference-online-resouce
 "Parse the Online Resource from the XML publication reference. Name and description are hardcoded."
 [pub-ref sanitize?]
 {:Linkage (util/with-default-url
            (url/format-url (value-of pub-ref "Online_Resource") sanitize?)
            sanitize?)
  :Name dif-online-resource-name
  :Description dif-online-resource-description})
