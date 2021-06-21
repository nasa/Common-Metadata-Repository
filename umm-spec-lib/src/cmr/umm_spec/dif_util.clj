(ns cmr.umm-spec.dif-util
  "Contains common definitions and functions for DIF9 and DIF10 metadata parsing and generation."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.common.util :as common-util]
   [cmr.umm-spec.url :as url]
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

(def dif-url-content-type->umm-url-types
 "Mapping from the dif URL Content Type type and subtype to UMM URLContentType, Type, and Subtype
  Pair of ['Type' 'Subtype'] -> {:URLContentType 'X' :Type 'Y' :Subtype 'Z'}
  Note UMM Subtype is not required so there may not be a subtype"
 {["DATA SET LANDING PAGE" nil] {:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"}
  ["DOWNLOAD SOFTWARE" nil] {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE"}
  ["DOWNLOAD SOFTWARE" "MOBILE APP"] {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE" :Subtype "MOBILE APP"}
  ["EXTENDED METADATA" nil] {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  ["GET CAPABILITIES" nil] {:URLContentType "DistributionURL" :Type "GET CAPABILITIES"}
  ["GET CAPABILITIES" "OpenSearch"] {:URLContentType "DistributionURL" :Type "GET CAPABILITIES" :Subtype "OpenSearch"}
  ["GET CAPABILITIES" "GIBS"] {:URLContentType "DistributionURL" :Type "GET CAPABILITIES" :Subtype "GIBS"}
  ["GET DATA" nil] {:URLContentType "DistributionURL" :Type "GET DATA"}
  ["GET DATA" "ALTERNATE ACCESS"] {:URLContentType "DistributionURL" :Type "GET DATA"}
  ["GET DATA" "APPEEARS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "APPEEARS"}
  ["GET DATA" "CERES Ordering Tool"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "CERES Ordering Tool"}
  ["GET DATA" "DATA COLLECTION BUNDLE"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA COLLECTION BUNDLE"}
  ["GET DATA" "DATA TREE"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA TREE"}
  ["GET DATA" "DATACAST URL"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"}
  ["GET DATA" "DIRECT DOWNLOAD"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DIRECT DOWNLOAD"}
  ["GET DATA" "EARTHDATA SEARCH"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  ["GET DATA" "Earthdata Search"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  ["GET DATA" "ECHO"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  ["GET DATA" "EDG"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  ["GET DATA" "EOSDIS DATA POOL"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"}
  ["GET DATA" "GDS"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "GRADS DATA SERVER (GDS)"}
  ["GET DATA" "GIOVANNI"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"}
  ["GET DATA" "KML"] {:URLContentType "DistributionURL" :Type "GET DATA"}
  ["GET DATA" "LAADS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"}
  ["GET DATA" "LANCE"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"}
  ["GET DATA" "LAS"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "LIVE ACCESS SERVER (LAS)"}
  ["GET DATA" "MIRADOR"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"}
  ["GET DATA" "MODAPS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"}
  ["GET DATA" "NOAA CLASS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"}
  ["GET DATA" "NOMADS"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOMADS"}
  ["GET DATA" "ON-LINE ARCHIVE"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA TREE"}
  ["GET DATA" "OPENDAP DATA"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
  ["GET DATA" "OPENDAP DATA (DODS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
  ["GET DATA" "OPENDAP DIRECTORY (DODS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
  ["GET DATA" "PORTAL"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "PORTAL"}
  ["GET DATA" "REVERB"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  ["GET DATA" "SSW"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SIMPLE SUBSET WIZARD (SSW)"}
  ["GET DATA" "Sub-Orbital Order Tool"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Sub-Orbital Order Tool"}
  ["GET DATA" "SUBSETTER"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SUBSETTER"}
  ["GET DATA" "THREDDS CATALOG"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  ["GET DATA" "THREDDS DATA"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  ["GET DATA" "THREDDS DIRECTORY"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  ["GET DATA" "USGS EARTH EXPLORER"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "USGS EARTH EXPLORER"}
  ["GET DATA","VERTEX"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VERTEX"}
  ["GET DATA","VIRTUAL COLLECTION"] {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VIRTUAL COLLECTION"}
  ["GET RELATED DATA SET METADATA (DIF)" nil] {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  ["GET RELATED SERVICE METADATA (SERF)" nil] {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  ["GET RELATED VISUALIZATION" nil] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  ["GET RELATED VISUALIZATION" "GIOVANNI"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"}
  ["GET RELATED VISUALIZATION" "MAP"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"}
  ["GET RELATED VISUALIZATION" "SOTO"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "SOTO"}
  ["GET RELATED VISUALIZATION" "WORLDVIEW"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "WORLDVIEW"}
  ["GET SERVICE" nil] {:URLContentType "DistributionURL" :Type "USE SERVICE API"}
  ["GET SERVICE" "ACCESS MAP VIEWER"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "MAP VIEWER"}
  ["GET SERVICE" "ACCESS MOBILE APP"] {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE" :Subtype "MOBILE APP"}
  ["GET SERVICE" "ACCESS WEB SERVICE"] {:URLContentType "DistributionURL" :Type "USE SERVICE API"}
  ["GET SERVICE" "DATA LIST"] {:URLContentType "DistributionURL" :Type "GET DATA"}
  ["GET SERVICE" "GET MAP SERVICE"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "MAP SERVICE"}
  ["GET SERVICE" "GET SOFTWARE PACKAGE"] {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE" :Subtype "MOBILE APP"}
  ["GET SERVICE" "GET WEB COVERAGE SERVICE (WCS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB COVERAGE SERVICE (WCS)"}
  ["GET SERVICE" "GET WEB FEATURE SERVICE (WFS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB FEATURE SERVICE (WFS)"}
  ["GET SERVICE" "GET WEB MAP FOR TIME SERIES"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"}
  ["GET SERVICE" "GET WEB MAP SERVICE (WMS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"}
  ["GET SERVICE" "GET WORKFLOW (SERVICE CHAIN)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "SERVICE CHAINING"}
  ["GET SERVICE" "OpenSearch"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OpenSearch"}
  ["GOTO WEB TOOL" nil] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL"}
  ["GOTO WEB TOOL" "HITIDE"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "HITIDE"}
  ["GOTO WEB TOOL" "LIVE ACCESS SERVER (LAS)"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "LIVE ACCESS SERVER (LAS)"}
  ["GOTO WEB TOOL" "MAP VIEWER"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "MAP VIEWER"}
  ["GOTO WEB TOOL" "SIMPLE SUBSET WIZARD (SSW)"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SIMPLE SUBSET WIZARD (SSW)"}
  ["GOTO WEB TOOL" "SUBSETTER"] {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SUBSETTER"}
  ["PROFESSIONAL HOME PAGE" nil] {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"}
  ["PROJECT HOME PAGE" nil] {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"}
  ["USE SERVICE API" nil] {:URLContentType "DistributionURL" :Type "USE SERVICE API"}
  ["USE SERVICE API" "GRADS DATA SERVER (GDS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "GRADS DATA SERVER (GDS)"}
  ["USE SERVICE API" "MAP SERVICE"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "MAP SERVICE"}
  ["USE SERVICE API" "OPENDAP DATA"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
  ["USE SERVICE API" "OpenSearch"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OpenSearch"}
  ["USE SERVICE API" "SERVICE CHAINING"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "SERVICE CHAINING"}
  ["USE SERVICE API" "TABULAR DATA STREAM (TDS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "TABULAR DATA STREAM (TDS)"}
  ["USE SERVICE API" "THREDDS DATA"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  ["USE SERVICE API" "WEB COVERAGE SERVICE (WCS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB COVERAGE SERVICE (WCS)"}
  ["USE SERVICE API" "WEB FEATURE SERVICE (WFS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB FEATURE SERVICE (WFS)"}
  ["USE SERVICE API" "WEB MAP SERVICE (WMS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"}
  ["USE SERVICE API" "WEB MAP TILE SERVICE (WMTS)"] {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP TILE SERVICE (WMTS)"}
  ["VIEW DATA SET LANDING PAGE" nil] {:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"}
  ["VIEW EXTENDED METADATA" nil] {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  ["VIEW IMAGES" nil] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  ["VIEW IMAGES" "BROWSE SAMPLE"] {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  ["VIEW PROFESSIONAL HOME PAGE" nil] {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"}
  ["VIEW PROJECT HOME PAGE" nil] {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"}
  ["VIEW RELATED INFORMATION" nil] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  ["VIEW RELATED INFORMATION" "ALGORITHM DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"}
  ["VIEW RELATED INFORMATION" "ANOMALIES"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ANOMALIES"}
  ["VIEW RELATED INFORMATION" "CASE STUDY"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"}
  ["VIEW RELATED INFORMATION" "DATA CENTER"] {:URLContentType "DataCenterURL" :Type "HOME PAGE"}
  ["VIEW RELATED INFORMATION" "DATA CITATION POLICY"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA CITATION POLICY"}
  ["VIEW RELATED INFORMATION" "DATA CONTACT"] {:URLContentType "DataContactURL" :Type "HOME PAGE"}
  ["VIEW RELATED INFORMATION" "DATA QUALITY"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"}
  ["VIEW RELATED INFORMATION" "DATA RECIPE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA RECIPE"}
  ["VIEW RELATED INFORMATION" "DELIVERABLES CHECKLIST"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"}
  ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "HOW-TO"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"}
  ["VIEW RELATED INFORMATION" "IMPORTANT NOTICE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "IMPORTANT NOTICE"}
  ["VIEW RELATED INFORMATION" "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "MICRO ARTICLE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "MICRO ARTICLE"}
  ["VIEW RELATED INFORMATION" "PI DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "PROCESSING HISTORY"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"}
  ["VIEW RELATED INFORMATION" "PRODUCT HISTORY"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"}
  ["VIEW RELATED INFORMATION" "PRODUCT QUALITY ASSESSMENT"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"}
  ["VIEW RELATED INFORMATION" "PRODUCT USAGE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"}
  ["VIEW RELATED INFORMATION" "PRODUCTION HISTORY"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION HISTORY"}
  ["VIEW RELATED INFORMATION" "PUBLICATIONS"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"}
  ["VIEW RELATED INFORMATION" "READ-ME"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"}
  ["VIEW RELATED INFORMATION" "REQUIREMENTS AND DESIGN"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"}
  ["VIEW RELATED INFORMATION" "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"}
  ["VIEW RELATED INFORMATION" "SCIENCE DATA PRODUCT VALIDATION"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"}
  ["VIEW RELATED INFORMATION" "USER FEEDBACK PAGE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK PAGE"}
  ["VIEW RELATED INFORMATION" "USER'S GUIDE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"}
  ["VIEW RELATED INFORMATION" "VIEW MICRO ARTICLE"] {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "MICRO ARTICLE"}})

(def umm-url-type->dif-umm-content-type
 "Map a combination of UMM URLContentType, Type, and Subtype (optional) to a dif url content type
 type and subtype. This is not dif->umm list flipped."
 {{:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"} ["DATA SET LANDING PAGE" nil]
  {:URLContentType "CollectionURL" :Type "DOI"} ["DATA SET LANDING PAGE" nil]
  {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"} ["EXTENDED METADATA" nil]
  {:URLContentType "CollectionURL" :Type "VIEW EXTENDED METADATA"} ["EXTENDED METADATA" nil]
  {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"} ["PROFESSIONAL HOME PAGE" nil]
  {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"} ["PROJECT HOME PAGE" nil]
  {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE"} ["DOWNLOAD SOFTWARE" nil]
  {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE" :Subtype "MOBILE APP"} ["DOWNLOAD SOFTWARE" "MOBILE APP"]
  {:URLContentType "DistributionURL" :Type "GET CAPABILITIES"} ["GET CAPABILITIES" nil]
  {:URLContentType "DistributionURL" :Type "GET CAPABILITIES" :Subtype "OpenSearch"} ["GET CAPABILITIES" "OpenSearch"]
  {:URLContentType "DistributionURL" :Type "GET CAPABILITIES" :Subtype "GIBS"} ["GET CAPABILITIES" "GIBS"]
  {:URLContentType "DistributionURL" :Type "GET DATA"} ["GET DATA" nil]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "APPEEARS"} ["GET DATA" "APPEEARS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "CERES Ordering Tool"} ["GET DATA" "CERES Ordering Tool"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA COLLECTION BUNDLE"} ["GET DATA" "DATA COLLECTION BUNDLE"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA TREE"} ["GET DATA" "DATA TREE"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"} ["GET DATA" "DATACAST URL"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DIRECT DOWNLOAD"} ["GET DATA" "DIRECT DOWNLOAD"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"} ["GET DATA" "Earthdata Search"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EARTHDATA SEARCH"} ["GET DATA" "Earthdata Search"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ECHO"} ["GET DATA" "Earthdata Search"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EDG"} ["GET DATA" "Earthdata Search"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"} ["GET DATA" "EOSDIS DATA POOL"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GDS"} ["USE SERVICE API" "GRADS DATA SERVER (GDS)"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"} ["GET DATA" "GIOVANNI"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "KML"} ["GET DATA" nil]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"} ["GET DATA" "LAADS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"} ["GET DATA" "LANCE"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAS"} ["GOTO WEB TOOL" "LIVE ACCESS SERVER (LAS)"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"} ["GET DATA" "MIRADOR"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"} ["GET DATA" "MODAPS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"} ["GET DATA" "NOAA CLASS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOMADS"} ["GET DATA" "NOMADS"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ON-LINE ARCHIVE"} ["GET DATA" "DATA TREE"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "PORTAL"} ["GET DATA" "PORTAL"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "REVERB"} ["GET DATA" "Earthdata Search"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Sub-Orbital Order Tool"} ["GET DATA" "Sub-Orbital Order Tool"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "USGS EARTH EXPLORER"} ["GET DATA" "USGS EARTH EXPLORER"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VERTEX"} ["GET DATA" "VERTEX"]
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VIRTUAL COLLECTION"} ["GET DATA" "VIRTUAL COLLECTION"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE"} ["USE SERVICE API" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MAP VIEWER"} ["GOTO WEB TOOL" "MAP VIEWER"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MOBILE APP"} ["DOWNLOAD SOFTWARE" "MOBILE APP"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS WEB SERVICE"} ["USE SERVICE API" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "DIF"} ["EXTENDED METADATA" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "MAP SERVICE"} ["USE SERVICE API" "MAP SERVICE"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "NOMADS"} ["GET DATA" "NOMADS"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA"} ["USE SERVICE API" "OPENDAP DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA (DODS)"} ["USE SERVICE API" "OPENDAP DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DIRECTORY (DODS)"} ["USE SERVICE API" "OPENDAP DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OpenSearch"} ["USE SERVICE API" "OpenSearch"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SERF"} ["EXTENDED METADATA" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SOFTWARE PACKAGE"} ["DOWNLOAD SOFTWARE" nil]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SSW"} ["GOTO WEB TOOL" "SIMPLE SUBSET WIZARD (SSW)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SUBSETTER"} ["GOTO WEB TOOL" "SUBSETTER"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS CATALOG"} ["USE SERVICE API" "THREDDS DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DATA"} ["USE SERVICE API" "THREDDS DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DIRECTORY"} ["USE SERVICE API" "THREDDS DATA"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB COVERAGE SERVICE (WCS)"} ["USE SERVICE API" "WEB COVERAGE SERVICE (WCS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB FEATURE SERVICE (WFS)"} ["USE SERVICE API" "WEB FEATURE SERVICE (WFS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP FOR TIME SERIES"} ["USE SERVICE API" "WEB MAP SERVICE (WMS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP SERVICE (WMS)"} ["USE SERVICE API" "WEB MAP SERVICE (WMS)"]
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WORKFLOW (SERVICE CHAIN)"} ["USE SERVICE API" "SERVICE CHAINING"]
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "HITIDE"} ["GOTO WEB TOOL" "HITIDE"]
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "LIVE ACCESS SERVER (LAS)"} ["GOTO WEB TOOL" "LIVE ACCESS SERVER (LAS)"]
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "MAP VIEWER"} ["GOTO WEB TOOL" "MAP VIEWER"]
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SIMPLE SUBSET WIZARD (SSW)"} ["GOTO WEB TOOL" "SIMPLE SUBSET WIZARD (SSW)"]
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SUBSETTER"} ["GOTO WEB TOOL" "SUBSETTER"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API"} ["USE SERVICE API" nil]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "GRADS DATA SERVER (GDS)"} ["USE SERVICE API" "GRADS DATA SERVER (GDS)"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "MAP SERVICE"} ["USE SERVICE API" "MAP SERVICE"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"} ["USE SERVICE API" "OPENDAP DATA"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OpenSearch"} ["USE SERVICE API" "OpenSearch"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "SERVICE CHAINING"} ["USE SERVICE API" "SERVICE CHAINING"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "TABULAR DATA STREAM (TDS)"} ["USE SERVICE API" "TABULAR DATA STREAM (TDS)"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"} ["USE SERVICE API" "THREDDS DATA"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB COVERAGE SERVICE (WCS)"} ["USE SERVICE API" "WEB COVERAGE SERVICE (WCS)"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB FEATURE SERVICE (WFS)"} ["USE SERVICE API" "WEB FEATURE SERVICE (WFS)"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"} ["USE SERVICE API" "WEB MAP SERVICE (WMS)"]
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP TILE SERVICE (WMTS)"} ["USE SERVICE API" "WEB MAP TILE SERVICE (WMTS)"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"} ["VIEW RELATED INFORMATION" nil]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM DOCUMENTATION"} ["VIEW RELATED INFORMATION" "ALGORITHM DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"} ["VIEW RELATED INFORMATION" "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"} ["VIEW RELATED INFORMATION" "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ANOMALIES"} ["VIEW RELATED INFORMATION" "ANOMALIES"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CALIBRATION DATA DOCUMENTATION"} ["VIEW RELATED INFORMATION" "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"} ["VIEW RELATED INFORMATION" "CASE STUDY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA CITATION POLICY"} ["VIEW RELATED INFORMATION" "DATA CITATION POLICY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"} ["VIEW RELATED INFORMATION" "DATA QUALITY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA RECIPE"} ["VIEW RELATED INFORMATION" "DATA RECIPE"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA USAGE"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"} ["VIEW RELATED INFORMATION" "DELIVERABLES CHECKLIST"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"} ["VIEW RELATED INFORMATION" "GENERAL DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"} ["VIEW RELATED INFORMATION" "HOW-TO"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "IMPORTANT NOTICE"} ["VIEW RELATED INFORMATION" "IMPORTANT NOTICE"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"} ["VIEW RELATED INFORMATION" "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "MICRO ARTICLE"} ["VIEW RELATED INFORMATION" "MICRO ARTICLE"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"} ["VIEW RELATED INFORMATION" "PI DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"} ["VIEW RELATED INFORMATION" "PROCESSING HISTORY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"} ["VIEW RELATED INFORMATION" "PRODUCT HISTORY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"} ["VIEW RELATED INFORMATION" "PRODUCT QUALITY ASSESSMENT"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"} ["VIEW RELATED INFORMATION" "PRODUCT USAGE"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION HISTORY"} ["VIEW RELATED INFORMATION" "PRODUCTION HISTORY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION VERSION HISTORY"} ["VIEW RELATED INFORMATION" "PRODUCTION HISTORY"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"} ["VIEW RELATED INFORMATION" "PUBLICATIONS"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "RADIOMETRIC AND GEOMETRIC CALIBRATION METHODS"} ["VIEW RELATED INFORMATION" "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"} ["VIEW RELATED INFORMATION" "READ-ME"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "RECIPE"} ["VIEW RELATED INFORMATION" "DATA RECIPE"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"} ["VIEW RELATED INFORMATION" "REQUIREMENTS AND DESIGN"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"} ["VIEW RELATED INFORMATION" "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"} ["VIEW RELATED INFORMATION" "SCIENCE DATA PRODUCT VALIDATION"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK"} ["VIEW RELATED INFORMATION" "USER FEEDBACK PAGE"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK PAGE"} ["VIEW RELATED INFORMATION" "USER FEEDBACK PAGE"]
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"} ["VIEW RELATED INFORMATION" "USER'S GUIDE"]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"} ["GET RELATED VISUALIZATION" nil]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIBS"} ["GET RELATED VISUALIZATION" "WORLDVIEW"]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"} ["GET RELATED VISUALIZATION" "GIOVANNI"]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"} ["GET RELATED VISUALIZATION" "MAP"]
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "SOTO"} ["GET RELATED VISUALIZATION" "SOTO"]})

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

(def ^:private dif10-dataset-languages
  "Set of Dataset_Languages supported in DIF10"
  (set (vals iso-639-2->dif10-dataset-language)))

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

(def dif-iso-topic-categories
  (keys dif-iso-topic-category->umm-iso-topic-category))

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

(defn parse-iso-topic-categories
  "Returns parsed UMM IsoTopicCategories"
  [doc]
  (values-at doc "DIF/ISO_Topic_Category"))

(defn parse-publication-reference-online-resouce
 "Parse the Online Resource from the XML publication reference."
 [pub-ref sanitize?]
 (when-let [linkage (value-of pub-ref "Online_Resource")]
   {:Linkage (url/format-url linkage sanitize?)}))
