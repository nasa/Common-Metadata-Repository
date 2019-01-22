(ns cmr.umm-spec.migration.related-url-migration-maps
  "This name space contains maps to facilitate the related-url-migration class.")

(def umm-1-10-umm-url-types->umm-1-11-umm-url-types
 "Mapping from the UMM-C v1.10 URLContentType, Type, and Subtype to the UMM v 1.11 URLContentType, Type, and Subtype
  Pair of {:URLContentType 'X' :Type 'Y' :Subtype 'Z'} -> {:URLContentType 'X' :Type 'Y' :Subtype 'Z'}
  Note UMM Subtype is not required so there may not be a subtype"
 {{:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"} {:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"}
  {:URLContentType "CollectionURL" :Type "DOI"} {:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"}
  {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"} {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"} {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"}
  {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"} {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"}
  {:URLContentType "DataCenterURL" :Type "HOME PAGE"} {:URLContentType "DataCenterURL" :Type "HOME PAGE"}
  {:URLContentType "DataContactURL" :Type "HOME PAGE"} {:URLContentType "DataContactURL" :Type "HOME PAGE"}
  {:URLContentType "DistributionURL" :Type "GET DATA"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EARTHDATA SEARCH"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ECHO"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EDG"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GDS"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "GRADS DATA SERVER (GDS)"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "KML"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAS"} {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "LIVE ACCESS SERVER (LAS)"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ON-LINE ARCHIVE"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA TREE"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "REVERB"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE"} {:URLContentType "DistributionURL" :Type "USE SERVICE API"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MAP VIEWER"} {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "MAP VIEWER"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MOBILE APP"} {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE" :Subtype "MOBILE APP"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS WEB SERVICE"} {:URLContentType "DistributionURL" :Type "USE SERVICE API"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "DIF"} {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "MAP SERVICE"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "MAP SERVICE"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "NOMADS"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOMADS"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA (DODS)"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DIRECTORY (DODS)"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OpenSearch"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OpenSearch"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SERF"} {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SOFTWARE PACKAGE"} {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SSW"} {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SIMPLE SUBSET WIZARD (SSW)"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SUBSETTER"} {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SUBSETTER"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS CATALOG"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DATA"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DIRECTORY"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB COVERAGE SERVICE (WCS)"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB COVERAGE SERVICE (WCS)"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB FEATURE SERVICE (WFS)"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB FEATURE SERVICE (WFS)"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP FOR TIME SERIES"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP SERVICE (WMS)"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"}
  {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WORKFLOW (SERVICE CHAIN)"} {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "Service Chaining"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CALIBRATION DATA DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA USAGE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION VERSION HISTORY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION HISTORY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "RADIOMETRIC AND GEOMETRIC CALIBRATION METHODS"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "RECIPE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA RECIPE"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK PAGE"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIBS"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "WORLDVIEW"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"}})

(def umm-1-11-umm-url-types->umm-1-10-umm-url-types
 "Mapping from the UMM-C v1.11 URLContentType, Type, and Subtype to the UMM v 1.10 URLContentType, Type, and Subtype
  Pair of {:URLContentType 'X' :Type 'Y' :Subtype 'Z'} -> {:URLContentType 'X' :Type 'Y' :Subtype 'Z'}
  Note UMM Subtype is not required so there may not be a subtype"
 {{:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"} {:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"}
  {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"} {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
  {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"} {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"}
  {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"} {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"}
  {:URLContentType "DataCenterURL" :Type "HOME PAGE"} {:URLContentType "DataCenterURL" :Type "HOME PAGE"}
  {:URLContentType "DataContactURL" :Type "HOME PAGE"} {:URLContentType "DataContactURL" :Type "HOME PAGE"}
  {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE"} {:URLContentType "DistributionURL" :Type "GET SERVICE"}
  {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE" :Subtype "MOBILE APP"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MOBILE APP"}
  {:URLContentType "DistributionURL" :Type "GET DATA"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "APPEEARS"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA COLLECTION BUNDLE"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA TREE"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "ON-LINE ARCHIVE"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DIRECT DOWNLOAD"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EARTHDATA SEARCH"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOMADS"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "NOMADS"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "PORTAL"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "USGS EARTH EXPLORER"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VERTEX"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VIRTUAL COLLECTION"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL"} {:URLContentType "DistributionURL" :Type "GET DATA"}
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "LIVE ACCESS SERVER (LAS)"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAS"}
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "MAP VIEWER"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "ACCESS MAP VIEWER"}
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SIMPLE SUBSET WIZARD (SSW)"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SSW"}
  {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SUBSETTER"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "SUBSETTER"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API"} {:URLContentType "DistributionURL" :Type "GET SERVICE"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "GRADS DATA SERVER (GDS)"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GDS"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "MAP SERVICE"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "MAP SERVICE"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OPENDAP DATA"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OpenSearch"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "OpenSearch"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "SERVICE CHAINING"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WORKFLOW (SERVICE CHAIN)"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "TABULAR DATA STREAM (TDS)"} {:URLContentType "DistributionURL" :Type "GET SERVICE"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "THREDDS DATA"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB COVERAGE SERVICE (WCS)"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB COVERAGE SERVICE (WCS)"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB FEATURE SERVICE (WFS)"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB FEATURE SERVICE (WFS)"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP SERVICE (WMS)"}
  {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP TILE SERVICE (WMTS)"} {:URLContentType "DistributionURL" :Type "GET SERVICE" :Subtype "WEB MAP SERVICE (WMS)"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ANOMALIES"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA CITATION POLICY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA RECIPE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "RECIPE"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "IMPORTANT NOTICE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CALIBRATION DATA DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "MICRO ARTICLE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION HISTORY"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION HISTORY"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK PAGE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK"}
  {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"} {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"}
  {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "WORLDVIEW"} {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIBS"}})

(def umm-1-11-related-url-types
  "Keys that map to the current set of related url keywords. This set allows the calling code to just
   concentrate on change to these keys instead of copying them across in each map.
   The keys contain the following structure {:URLContentType 'X' :Type 'Y' :Subtype 'Z'}
   Note UMM Subtype is not required so there may not be a subtype"
  [{:URLContentType "CollectionURL" :Type "DATA SET LANDING PAGE"}
   {:URLContentType "CollectionURL" :Type "EXTENDED METADATA"}
   {:URLContentType "CollectionURL" :Type "PROFESSIONAL HOME PAGE"}
   {:URLContentType "CollectionURL" :Type "PROJECT HOME PAGE"}
   {:URLContentType "DataCenterURL" :Type "HOME PAGE"}
   {:URLContentType "DataContactURL" :Type "HOME PAGE"}
   {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE"}
   {:URLContentType "DistributionURL" :Type "DOWNLOAD SOFTWARE" :Subtype "MOBILE APP"}
   {:URLContentType "DistributionURL" :Type "GET DATA"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "APPEEARS"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA COLLECTION BUNDLE"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATA TREE"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DATACAST URL"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "DIRECT DOWNLOAD"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "EOSDIS DATA POOL"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GIOVANNI"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LAADS"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MIRADOR"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "MODAPS"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOAA CLASS"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "NOMADS"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "PORTAL"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "USGS EARTH EXPLORER"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VERTEX"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "VIRTUAL COLLECTION"}
   {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL"}
   {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "LIVE ACCESS SERVER (LAS)"}
   {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "MAP VIEWER"}
   {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SIMPLE SUBSET WIZARD (SSW)"}
   {:URLContentType "DistributionURL" :Type "GOTO WEB TOOL" :Subtype "SUBSETTER"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "GRADS DATA SERVER (GDS)"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "MAP SERVICE"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OPENDAP DATA"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "OpenSearch"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "SERVICE CHAINING"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "TABULAR DATA STREAM (TDS)"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB COVERAGE SERVICE (WCS)"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB FEATURE SERVICE (WFS)"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP SERVICE (WMS)"}
   {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB MAP TILE SERVICE (WMTS)"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM DOCUMENTATION"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ANOMALIES"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "CASE STUDY"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA CITATION POLICY"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA QUALITY"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DATA RECIPE"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "DELIVERABLES CHECKLIST"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "GENERAL DOCUMENTATION"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "HOW-TO"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "IMPORTANT NOTICE"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "MICRO ARTICLE"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PI DOCUMENTATION"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PROCESSING HISTORY"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT HISTORY"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT QUALITY ASSESSMENT"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCT USAGE"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PRODUCTION HISTORY"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "PUBLICATIONS"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "READ-ME"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "REQUIREMENTS AND DESIGN"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "SCIENCE DATA PRODUCT VALIDATION"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER FEEDBACK PAGE"}
   {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "USER'S GUIDE"}
   {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION"}
   {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "GIOVANNI"}
   {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "MAP"}
   {:URLContentType "VisualizationURL" :Type "GET RELATED VISUALIZATION" :Subtype "WORLDVIEW"}])

(def umm-1-12-umm-url-types->umm-1-11-umm-url-types
  "Mapping from the UMM-C v1.12 URLContentType, Type, and Subtype to the UMM v 1.11 URLContentType, Type, and Subtype
   Pair of {:URLContentType 'X' :Type 'Y' :Subtype 'Z'} -> {:URLContentType 'X' :Type 'Y' :Subtype 'Z'}
   Note UMM Subtype is not required so there may not be a subtype"
  {{:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "GoLIVE Portal"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "PORTAL"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "IceBridge Portal"} {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "PORTAL"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Order"} {:URLContentType "DistributionURL" :Type "GET DATA"}
   {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Subscribe"} {:URLContentType "DistributionURL" :Type "GET DATA"}})
