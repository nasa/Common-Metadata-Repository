(ns cmr.umm-spec.xml-to-umm-mappings.echo10.related-url
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.related-url :as related-url]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.url :as url]))

(def url-content-types
 "URL content type enums"
 #{"DistributionURL" "VisualizationURL" "PublicationURL" "CollectionURL" "DataCenterURL" "DataContactURL"})

(def online-resource-type->related-url-types
 "Map from ECHO online resource type to UMM RelatedURL URLContentType, Type,
 and Subtype."
 {"VIEW RELATED INFORMATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "DOI URL" {:URLContentType "CollectionURL", :Type "DOI"}
  "BROWSE" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "Guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Homepage" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Software" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SOFTWARE PACKAGE"}
  "DOI" {:URLContentType "CollectionURL", :Type "DOI"}
  "Citing GHRC Data" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "GET DATA : MIRADOR" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "MIRADOR"}
  "VIEW RELATED INFORMATION : USER'S GUIDE" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "GET DATA : REVERB" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "REVERB"}
  "GET DATA : ON-LINE ARCHIVE" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "ON-LINE ARCHIVE"}
  "Data Tool/Application" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SOFTWARE PACKAGE"}
  "Browse" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "Collection Quality Summary" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "DATA QUALITY"}
  "GET DATA : OPENDAP DATA (DODS)" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "OPENDAP DATA (DODS)"}
  "GET DATA : ECHO" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "ECHO"}
  "VIEW PROJECT HOME PAGE" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "GET RELATED VISUALIZATION" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "GET SERVICE : GET WEB MAP SERVICE (WMS)" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "WEB MAP SERVICE (WMS)"}
  "GET SERVICE : GET WEB COVERAGE SERVICE (WCS)" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "WEB COVERAGE SERVICE (WCS)"}
  "GET DATA : DATACAST URL" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "DATACAST URL"}
  "BROWSE SAMPLE" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "PI Documentation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PI DOCUMENTATION"}
  "DatasetDisclaimer" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "ECSCollGuide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "MiscInformation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Collection Guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Type:GET DATA Subtype:REVERB" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "REVERB"}
  "Type:GET DATA Subtype:ON-LINE ARCHIVE" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "ON-LINE ARCHIVE"}
  "Type:VIEW PROJECT HOME PAGE" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Type:GET DATA" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "Type:VIEW RELATED INFORMATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DA_DIRECT_ACCESS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Project Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Get Data" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "Reference Materials" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "VIEW RELATED INFORMATION : GENERAL DOCUMENTATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "VIEW RELATED INFORMATION : HOW-TO" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "HOW-TO"}
  "Related URLs" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "static URL" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "GET RELATED SERVICE METADATA (SERF)" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SERF"}
  "GET SERVICE : GET SOFTWARE PACKAGE" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SOFTWARE PACKAGE"}
  "GET DATA : SUBSETTER" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SUBSETTER"}
  "GET DATA : GIOVANNI" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "GIOVANNI"}
  "GET DATA : GDS" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "GDS"}
  "Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Landing Page" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "User Support" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "GET DATA" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "Quality Summary" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "DATA QUALITY"}
  "VIEW EXTENDED METADATA" {:URLContentType "CollectionURL", :Type "EXTENDED METADATA"}
  "GET SERVICE : GET WEB MAP FOR TIME SERIES" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "WEB MAP FOR TIME SERIES"}
  "Algorithm Information" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"}
  "Micro Article" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "OPeNDAP" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "OPENDAP DATA"}
  "Publications" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PUBLICATIONS"}
  "Data Documentation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "VIEW RELATED INFORMATION : PI DOCUMENTATION" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PI DOCUMENTATION"}
  "Important Notice" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "text/html" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Anomalies" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Collection DOI" {:URLContentType "CollectionURL", :Type "DOI"}
  "GET DATA : LANCE" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "LANCE"}
  "Type:GET DATA Subtype:LANCE" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "LANCE"}
  "Type:GET DATA Subtype:ECHO" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "ECHO"}
  "Type:GET DATA Subtype:LAADS" {:URLContentType "DistributionURL", :Type "GET DATA", :Subtype "LAADS"}
  "Alternate Data Access" {:URLContentType "DistributionURL", :Type "GET DATA"}
  "Worldview Imagery" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION", :Subtype "GIBS"}
  "GET SERVICE" {:URLContentType "DistributionURL", :Type "GET SERVICE"}
  "PORTAL_DA_READ_SOFTWARE" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SOFTWARE PACKAGE"}
  "PORTAL_DOC_USERS_GUIDE" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Thumbnail" {:URLContentType "VisualizationURL", :Type "GET RELATED VISUALIZATION"}
  "Reference" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DOC_JOURNAL_REFERENCES" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PUBLICATIONS"}
  "PORTAL_DOC_ADDITIONAL_SITES" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DOC_PROJECT_MATERIALS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PORTAL_DA_TOOLS_AND_SERVICES" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SUBSETTER"}
  "PORTAL_DOC_KNOWN_ISSUES" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Web Page" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Provider Webpage" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "JPL ESG Link" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION"}
  "Guide Document" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Project home page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "GHRSST Portal Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "PORTAL_DOC_ANNOUNCEMENTS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Calibration/validation data" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "CALIBRATION DATA DOCUMENTATION"}
  "PORTAL_DOC_FAQS" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Get Service" {:URLContentType "DistributionURL", :Type "GET SERVICE"}
  "Ice Home Page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "EXTRACT_NETCDF4" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "SOFTWARE PACKAGE"}
  "Website" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Web site" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "Data access (FTP)" {:URLContentType "DistributionURL", :Type "GET SERVICE"}
  "Data access (OpenDAP)" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "OPENDAP DATA"}
  "Product Quality Monitoring Page" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PRODUCT QUALITY ASSESSMENT"}
  "Project Homepage" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Data access (OPeNDAP)" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "OPENDAP DATA"}
  "Project Website" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Project homepage" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "Release Notes" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION"}
  "data set guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Dataset User Guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "Documentation" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "README" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "READ-ME"}
  "ersst.v2.readme.txt" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "READ-ME"}
  "dataset description" {:URLContentType "CollectionURL", :Type "DATA SET LANDING PAGE"}
  "User guide" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "USER'S GUIDE"}
  "PORTAL_DA" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "PO.DAAC SPURS Mission page" {:URLContentType "CollectionURL", :Type "PROJECT HOME PAGE"}
  "PDF" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "GENERAL DOCUMENTATION"}
  "GET DATA : THREDDS DIRECTORY" {:URLContentType "DistributionURL", :Type "GET SERVICE", :Subtype "THREDDS DIRECTORY"}
  "VIEW RELATED INFORMATION : PRODUCT HISTORY" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "PRODUCT HISTORY"}
  "Algorithm Info" {:URLContentType "PublicationURL", :Type "VIEW RELATED INFORMATION", :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"}})

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
       :Subtype (get (set/map-invert related-url/subtype->abbreviation) (second types) (second types))}
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
         "GET DATA" {:GetData nil}
         "GET SERVICE" {:GetService (when-not (or (= "Not provided" mime-type)
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
  (for [resource (select doc "/Collection/OnlineAccessURLs/OnlineAccessURL")]
    {:URL (url/format-url (value-of resource "URL") sanitize?)
     :Description (value-of resource "URLDescription")
     :URLContentType "DistributionURL"
     :Type "GET DATA"
     :GetData nil}))

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
