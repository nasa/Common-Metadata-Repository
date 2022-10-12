(ns cmr.umm-spec.xml-to-umm-mappings.echo10.related-url
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.common.util :as util]
   [cmr.umm-spec.related-url :as related-url]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.url :as url]))

(def url-content-types
 "URL content type enums"
 #{"DistributionURL" "VisualizationURL" "PublicationURL" "CollectionURL" "DataCenterURL" "DataContactURL"})

(def online-resource-type->related-url-types
 "Map from ECHO online resource type to UMM RelatedURL URLContentType, Type,
 and Subtype."
 {"ALGORITHM DOCUMENTATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ALGORITHM DOCUMENTATION"}
  "Algorithm Info" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ALGORITHM DOCUMENTATION"}
  "Algorithm Information" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ALGORITHM DOCUMENTATION"}
  "ALGORITHM THEORETICAL BASIS DOCUMENT" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"}
  "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"}
  "ALTERNATE ACCESS" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "Alternate Data Access" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "ANOMALIES" {:URLContentType "PublicationURL" :Type "VIEW RELATED INFORMATION" :Subtype "ANOMALIES"}
  "Anomalies" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ANOMALIES"}
  "BROWSE" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "Browse" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "BROWSE SAMPLE" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "Calibration/validation data" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"}
  "calibration" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION"}
  "Citing GHRC Data" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "DATA CITATION POLICY"}
  "Collection DOI" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "Collection Guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Collection Quality Summary" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "DATA QUALITY"}
  "CollectionURL : PROJECT HOME PAGE" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Data access (FTP)" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "Data Documentation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "DATA RECIPE" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "DATA RECIPE"}
  "data set guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "DATA SET LANDING PAGE" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "dataset description" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "Dataset User Guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "DatasetDisclaimer" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Documentation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "DOI" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "DOI URL" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "DOWNLOAD SOFTWARE" {:URLContentType "DistributionURL", :Type "DOWNLOAD SOFTWARE"}
  "ECSCollGuide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "ersst.v2.readme.txt" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "READ-ME"}
  "EXTRACT_NETCDF4" {:URLContentType "DistributionURL", :Type "DOWNLOAD SOFTWARE"}
  "GENERAL DOCUMENTATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "GET CAPABILITIES" {:URLContentType "DistributionURL", :Type "GET CAPABILITIES"}
  "GET CAPABILITIES : OpenSearch" {:URLContentType "DistributionURL", :Type "GET CAPABILITIES" :Subtype "OpenSearch"}
  "GET CAPABILITIES : GIBS" {:URLContentType "DistributionURL", :Type "GET CAPABILITIES" :Subtype "GIBS"}
  "Get Data" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "GET RELATED VISUALIZATION" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "GET RELATED VISUALIZATION : SOTO" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION" :Subtype "SOTO"}
  "GET SERVICE" {:URLContentType "DistributionURL", :Type "USE SERVICE API"}
  "Get Service" {:URLContentType "DistributionURL", :Type "USE SERVICE API"}
  "GHRSST Portal Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "GOTO WEB TOOL" {:URLContentType "DistributionURL", :Type "GOTO WEB TOOL"}
  "GOTO WEB TOOL : HITIDE" {:URLContentType "DistributionURL", :Type "GOTO WEB TOOL" :Subtype "HITIDE"}
  "Guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Guide Document" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Homepage" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "HOW-TO" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "HOW-TO"}
  "Ice Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Important Notice" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "IMPORTANT NOTICE"}
  "JPL ESG Link" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION"}
  "LANCE" {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "LANCE"}
  "Landing Page" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "MICRO ARTICLE" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "MICRO ARTICLE"}
  "MiscInformation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "OPENDAP DATA" {:URLContentType "DistributionURL", :Type "USE SERVICE API", :Subtype "OPENDAP DATA"}
  "PDF" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PI Documentation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PI DOCUMENTATION"}
  "PI DOCUMENTATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PI DOCUMENTATION"}
  "PO.DAAC SPURS Mission page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "PORTAL_DA" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "PORTAL_DA_DIRECT_ACCESS" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "DIRECT DOWNLOAD"}
  "PORTAL_DA_READ_SOFTWARE" {:URLContentType "DistributionURL", :Type "DOWNLOAD SOFTWARE"}
  "PORTAL_DA_TOOLS_AND_SERVICES" {:URLContentType "DistributionURL", :Type "GOTO WEB TOOL"}
  "PORTAL_DOC_ADDITIONAL_SITES" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "PORTAL_DOC_ANNOUNCEMENTS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DOC_FAQS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DOC_JOURNAL_REFERENCES" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PUBLICATIONS"}
  "PORTAL_DOC_KNOWN_ISSUES" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DOC_PROJECT_MATERIALS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DOC_USERS_GUIDE" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "PROCESSING HISTORY" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PROCESSING HISTORY"}
  "PROJECT HOME PAGE" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Project Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Provider Webpage" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PUBLICATIONS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PUBLICATIONS"}
  "Publications" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PUBLICATIONS"}
  "PublicationURL : VIEW RELATED INFORMATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION"}
  "Quality Summary" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "DATA QUALITY"}
  "README" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "READ-ME"}
  "Reference" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Reference Materials" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Release Notes" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION"}
  "Software" {:URLContentType "DistributionURL", :Type "DOWNLOAD SOFTWARE"}
  "text/html" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "THREDDS DIRECTORY" {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "THREDDS DATA"}
  "Thumbnail" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "User guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "User Support" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "USER's GUIDE" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "VIEW DATA SET LANDING PAGE" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "VIEW IMAGES" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "VIEW PROJECT HOME PAGE" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "VIEW RELATED INFORMATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION"}
  "WEB COVERAGE SERVICE (WCS)" {:URLContentType "DistributionURL" :Type "USE SERVICE API" :Subtype "WEB COVERAGE SERVICE (WCS)"}
  "Web Page" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Web site" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Website" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "WORLDVIEW" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION", :Subtype "WORLDVIEW"}
  "Worldview Imagery" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION", :Subtype "WORLDVIEW"}})

(def ^:private get-data-subtypes
 "Defines a list of KMS Subtypes for RelatedURL GET DATA type."
  ["GIOVANNI" "NOAA CLASS" "MIRADOR" "USGS EARTH EXPLORER" "SUBSCRIBE" "CERES ORDERING TOOL" "EARTHDATA SEARCH" "SUB-ORBITAL ORDER TOOL" 
   "DATA TREE" "ORDER" "LAADS" "DATACAST URL" "LANCE" "VIRTUAL COLLECTION" "MLHUB" "MODAPS" "PORTAL" "ICEBRIDGE PORTAL" "APPEEARS" 
   "DATA COLLECTION BUNDLE" "NOMADS" "VERTEX" "DIRECT DOWNLOAD" "GOLIVE PORTAL" "EOSDIS DATA POOL"])

(defn- get-url-subtype
  "Return the subtype with known abbreviations and GET DATA subtypes sanitized."
  [url-type url-subtype]
  (let [subtype (get (set/map-invert related-url/subtype->abbreviation) url-subtype url-subtype)]
   (if (= "GET DATA" url-type)
     (when (some #{(util/safe-uppercase subtype)} get-data-subtypes)
       subtype)
     subtype)))

(defn- get-url-type
 "Get UMM URL type from ECHO online resource type. The resource type could be the
 URLContentType, Type, and Subtype delimited by :, otherwise try lookup in the table.
 Default is used if cannot be found in the table"
 [resource-type]
 (if resource-type
  (let [types (str/split resource-type #" : ")]
   (if (> (count types) 1)
    (if (contains? url-content-types (first types)) ; Check for valid URLContentType
     ;; No subtype
     {:URLContentType (first types)
      :Type (second types)}
     (if-let [url-content-type (su/type->url-content-type (first types))]
      {:URLContentType url-content-type
       :Type (first types)
       :Subtype (get-url-subtype (first types) (second types))}
      (get online-resource-type->related-url-types resource-type su/default-url-type)))
    (get online-resource-type->related-url-types resource-type su/default-url-type)))
  su/default-url-type))

(defn- parse-online-resource-urls
  "Parse online resource urls"
  [doc sanitize?]
  (for [resource (select doc "/Collection/OnlineResources/OnlineResource")
        :let [resource-type (value-of resource "Type")
              url-type (get-url-type resource-type)
              description (value-of resource "Description")
              mime-type (value-of resource "MimeType")]]
    (merge
     url-type
     {:URL (url/format-url (value-of resource "URL") sanitize?)
      :Description description}
     (when (= "DistributionURL" (:URLContentType url-type))
       (case (:Type url-type)
         (or "GET DATA"
             "GET CAPABILITIES") {:GetData (when (seq mime-type)
                                             {:Format (su/with-default nil sanitize?)
                                              :MimeType mime-type
                                              :Size 0.0
                                              :Unit "KB"})}
         "USE SERVICE API" {:GetService (when-not (or (= "Not provided" mime-type)
                                                      (nil? mime-type))
                                          {:MimeType mime-type
                                           :FullName (su/with-default nil sanitize?)
                                           :DataID (su/with-default nil sanitize?)
                                           :DataType (su/with-default nil sanitize?)
                                           :Protocol (su/with-default nil sanitize?)})}
         nil)))))

(defn- parse-online-access-urls
  "Parse online access urls"
  [doc sanitize?]
  (for [resource (select doc "/Collection/OnlineAccessURLs/OnlineAccessURL")
        :let [mime-type (value-of resource "MimeType")]]
    {:URL (url/format-url (value-of resource "URL") sanitize?)
     :Description (value-of resource "URLDescription")
     :URLContentType "DistributionURL"
     :Type "GET DATA"
     :GetData (when (seq mime-type)
                {:Format (su/with-default nil sanitize?)
                 :MimeType mime-type
                 :Size 0.0
                 :Unit "KB"})}))

(defn- parse-browse-urls
  "Parse browse urls"
  [doc sanitize?]
  (for [resource (select doc "/Collection/AssociatedBrowseImageUrls/ProviderBrowseUrl")]
    {:URL (url/format-url (value-of resource "URL") sanitize?)
     :Description (value-of resource "Description")
     :URLContentType "VisualizationURL"
     :Type "GET RELATED VISUALIZATION"}))

(defn parse-related-urls
  "Returns related-urls elements from a parsed XML structure"
  [doc sanitize?]
  (seq (concat (parse-online-access-urls doc sanitize?)
               (parse-online-resource-urls doc sanitize?)
               (parse-browse-urls doc sanitize?))))
