(ns cmr.umm-spec.related-url-titles)

(def related-url-titles
  "Contains mappings for RelatedURL titles. Mapped as URLContentType->Type->Subtype->Title"

  ;; DistributionURL
  {"DistributionURL"
   ;; DistributionURL/DOWNLOAD SOFTWARE
   {"DOWNLOAD SOFTWARE"
    {"default" "Downloadable software applications"
     "MOBILE APP" "Downloadable mobile applications"}

    ;; DistributionURL/GET DATA
    "GET DATA"
    {"default" "Download this dataset"
     "APPEEARS" "Download this dataset through APPEEARS"
     "CERES Ordering Tool" "Download this dataset through the CERES Ordering Tool"
     "DATA COLLECTION BUNDLE" "Download this dataset as one complete package"
     "DATA TREE" "Download this dataset through a directory map"
     "DATACAST URL" "Download this dataset through a datacast URL."
     "DIRECT DOWNLOAD" "Download this dataset"
     "Earthdata Search" "Download this dataset through Earthdata Search"
     "EOSDIS DATA POOL" "Download this dataset through its archiver."
     "GIOVANNI" "Download this dataset through GIOVANNI"
     "LAADS" "Download this dataset through LAADS"
     "LANCE" "Download this dataset through LANCE"
     "MIRADOR" "Download this dataset through MIRADOR"
     "MODAPS" "Download this dataset through MODAPS"
     "NOAA CLASS" "Download this dataset through NOAA CLASS"
     "NOMADS" "Download this dataset through NOMADS"
     "PORTAL" "Download this dataset through a portal"
     "Sub-Orbital Order Tool" "Download this dataset through the Sub-Orbital Order Tool"
     "USGS EARTH EXPLORER" "Download this dataset through USGS Earth Explorer"
     "VERTEX" "Download this dataset through VERTEX"
     "VIRTUAL COLLECTION" "Download this virtual dataset"}

    ;; DistributionURL/GET CAPABILITIES
    "GET CAPABILITIES"
    {"default" "Retrieve the Get Capabilities document"
     "OpenSearch" "Retrieve the OpenSearch Get Capabilities document"
     "GIBS" "Retrieve the GIBS Get Capabilities document"}

    ;; DistributionURL/GOTO WEB TOOL
    "GOTO WEB TOOL"
    {"default" "Use this dataset in a web based tool"
     "HITIDE" "Use this dataset in the HITIDE tool"
     "LIVE ACCESS SERVER (LAS)" "Use this dataset in the web based Live Access Server (LAS)"
     "MAP VIEWER" "Use this dataset in a web based map viewerf"
     "SIMPLE SUBSET WIZARD (SSW)" "Subset this dataset using a web based simple subset wizard (SSW)"
     "SUBSETTER" "Subset this dataset using a web based subsetter"}

    ;; DistributionURL/USE SERVICE API
    "USE SERVICE API"
    {"default" "Use an online web service to download this dataset's data"
     "GRADS DATA SERVER (GDS)" "Use GRADS DATA SERVER (GDS) to access the dataset's data"
     "MAP SERVICE" "Use a map service to access the dataset's data"
     "OPENDAP DATA" "Use OPeNDAP to access the dataset's data"
     "OpenSearch" "Use OpenSearch to download the dataset's data"
     "SERVICE CHAINING" "Use Service Chaining to download the dataset's data"
     "TABULAR DATA STREAM (TDS)" "Use Tabular Data Stream (TDS) service to download the dataset's data"
     "THREDDS DATA" "Use THREDDS DATA to download the dataset's data"
     "WEB COVERAGE SERVICE (WCS)" "Use Web Coverage Service (WCS) to download the dataset's data"
     "WEB FEATURE SERVICE (WFS)" "Use Web Feature Service (WFS) to download the dataset's data"
     "WEB MAP SERVICE (WMS)" "Use Web Map Service (WMS) to download the dataset's data"
     "WEB MAP TILE SERVICE (WMTS)" "Use Web Map Tile Service (WMTS) to download the dataset's data"}}

   "VisualizationURL"
   ;; VisualizationURL/GET RELATED VISUALIZATION
   {"GET RELATED VISUALIZATION"
    {"default" "Get a related visualization"
     "GIOVANNI" "Get a related visualization through GIOVANNI"
     "MAP" "Get a related map visualization"
     "SOTO" "Get a visualization through SOTO"
     "WORLDVIEW" "Get a related visualization through WORLDVIEW"}}

   "CollectionURL"
   ;; CollectionURL/DATA SET LANDING PAGE
   {"DATA SET LANDING PAGE"
    {"default" "This dataset's landing page"}

    ;; CollectionURL/EXTENDED METADATA
    "EXTENDED METADATA"
    {"default" "Access to this dataset's extended metadata"}

    ;; CollectionURL/PROFESSIONAL HOME PAGE
    "PROFESSIONAL HOME PAGE"
    {"default" "The dataset's home page"}

    ;; CollectionURL/PROJECT HOME PAGE
    "PROJECT HOME PAGE"
    {"default" "The dataset's project home page"}}

   "PublicationURL"
   ;; PublicationURL/VIEW RELATED INFORMATION
   {"VIEW RELATED INFORMATION"
    {"default" "View information related to this dataset"
     "ALGORITHM DOCUMENTATION" "View the documentation for this dataset's algorithms"
     "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)" "View this dataset's algorithm theoretical basis document"
     "ANOMALIES" "View this dataset's documented anomalies"
     "CASE STUDY" "View this dataset's documented case studies"
     "DATA CITATION POLICY" "View this dataset's data citation policy"
     "DATA QUALITY" "View this dataset's data quality document"
     "DATA RECIPE" "View this dataset's data recipes"
     "DELIVERABLES CHECKLIST" "View this dataset's deliverables checklist"
     "GENERAL DOCUMENTATION" "View documentation related to this dataset"
     "HOW-TO" "View this dataset's how-to documentation"
     "IMPORTANT NOTICE" "View an important notice for this dataset"
     "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION" "View this dataset's calibration documentation"
     "MICRO ARTICLE" "View a micro article on this dataset"
     "PI DOCUMENTATION" "View the primary investigator's documentation for this dataset"
     "PROCESSING HISTORY" "View this dataset's processing history"
     "PRODUCT HISTORY" "View this dataset's product history"
     "PRODUCT QUALITY ASSESSMENT" "View this dataset's product quality assessment"
     "PRODUCT USAGE" "View this dataset's product usage"
     "PRODUCTION HISTORY" "View this dataset's production history"
     "PUBLICATIONS" "View this dataset's publications"
     "READ-ME" "View this dataset's read me document"
     "REQUIREMENTS AND DESIGN" "View this dataset's requirements and design documentation"
     "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION" "View this dataset's science data product software documentation"
     "SCIENCE DATA PRODUCT VALIDATION" "View this dataset's science data product validation documentation"
     "USER FEEDBACK PAGE" "Access this dataset's users feedback page"
     "USER'S GUIDE" "View this dataset's user's guide"}}

   "DataCenterURL"
   ;; DataCenterURL/HOME PAGE
   {"HOME PAGE"
    {"default" "Visit this dataset's data center's home page"}}

   "DataContactURL"
   ;; DataContactURL/HOME PAGE
   {"HOME PAGE"
    {"default" "Visit this dataset's data center's contact home page"}}})
