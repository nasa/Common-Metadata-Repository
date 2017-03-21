(ns cmr.umm-spec.validation.related-url
  "Defines validations for Related Urls throughout UMM collections."
  (:require
   [clojure.string :as str]
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.validation.umm-spec-validation-utils :as vu])
  (:import
   (org.apache.commons.validator.routines UrlValidator)))

(def valid-url-content-types-map
  {"DistributionURL" {"GET SERVICE" ["DATACAST URL"
                                     "EARTHDATA SEARCH"
                                     "ECHO"
                                     "EDG"
                                     "EOSDIS DATA POOL"
                                     "GDS"
                                     "GIOVANNI"
                                     "KML"
                                     "LAADS"
                                     "LANCE"
                                     "LAS"
                                     "MIRADOR"
                                     "MODAPS"
                                     "NOAA CLASS"
                                     "ON-LINE ARCHIVE"
                                     "REVERB"]
                      "GET DATA" ["ACCESS MAP VIEWER"
                                  "ACCESS MOBILE APP"
                                  "ACCESS WEB SERVICE"
                                  "DIF"
                                  "MAP SERVICE"
                                  "NOMADS"
                                  "OPENDAP DATA"
                                  "OPENDAP DATA (DODS)"
                                  "OPENDAP DIRECTORY (DODS)"
                                  "OpenSearch"
                                  "SERF"
                                  "SOFTWARE PACKAGE"
                                  "SSW"
                                  "SUBSETTER"
                                  "THREDDS CATALOG"
                                  "THREDDS DATA"
                                  "THREDDS DIRECTORY"
                                  "WEB COVERAGE SERVICE (WCS)"
                                  "WEB FEATURE SERVICE (WFS)"
                                  "WEB MAP FOR TIME SERIES"
                                  "WEB MAP SERVICE (WMS)"
                                  "WORKFLOW (SERVICE CHAIN)"]}
   "VisualizationURL" {"GET RELATED VISUALIZATION" ["GIBS" "GIOVANNI"]}
   "CollectionURL" {"DATA SET LANDING PAGE" []
                    "DOI" []
                    "EXTENDED METADATA" []
                    "PROFESSIONAL HOME PAGE" []
                    "PROJECT HOME PAGE" []}
   "PublicationURL" {"VIEW RELATED INFORMATION" ["ALGORITHM THEORETICAL BASIS DOCUMENT"
                                                 "CALIBRATION DATA DOCUMENTATION"
                                                 "CASE STUDY"
                                                 "DATA QUALITY"
                                                 "DATA USAGE"
                                                 "DELIVERABLES CHECKLIST"
                                                 "GENERAL DOCUMENTATION"
                                                 "HOW-TO"
                                                 "PI DOCUMENTATION"
                                                 "PROCESSING HISTORY"
                                                 "PRODUCTION VERSION HISTORY"
                                                 "PRODUCT QUALITY ASSESSMENT"
                                                 "PRODUCT USAGE"
                                                 "PRODUCT HISTORY"
                                                 "PUBLICATIONS"
                                                 "RADIOMETRIC AND GEOMETRIC CALIBRATION METHODS"
                                                 "READ-ME"
                                                 "RECIPE"
                                                 "REQUIREMENTS AND DESIGN"
                                                 "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION"
                                                 "SCIENCE DATA PRODUCT VALIDATION"
                                                 "USER FEEDBACK"
                                                 "USER'S GUIDE"]}
   "DataCenterURL" {"HOME PAGE" []}
   "DataContactURL" {"HOME PAGE" []}})

(defn valid-url-content-types-combo?
  "Returns true if valid SubType for URLContentType"
  [url-content-type type sub-type]
  (when (and (some (partial = type) (keys (get valid-url-content-types-map url-content-type)))
             (or (nil? sub-type)
                 (some (partial = sub-type) (get-in valid-url-content-types-map [url-content-type type]))))
    true))

(defn related-url-type-validation
  "Validate the Type being valid for the accompanying URLContentType"
  [field-path value]
  (let [url-content-type (:URLContentType value)
        type (:Type value)
        sub-type (:SubType value)]
    (when-not (valid-url-content-types-combo? url-content-type type sub-type)
      {field-path
       [(vu/escape-error-string (format "URLContentType: %s, Type: %s, SubType: %s is not a vaild URLContentType/Type/SubType combination."
                                        url-content-type type sub-type))]})))

(defn url-validation
  "Validate the URL. Return nil if no errors and the field path and error if the URL
  is not valid."
  [field-path value]
  (let [validator (UrlValidator. (into-array ["http" "ftp" "https" "file"]))]
    (when (and (not= value su/not-provided-url)
               (not (.isValid validator value)))
      {field-path
       ;; Escape the %, because the error messages go through a format, which will throw an error
       ;; Do the escape after the format here, so it doesn't get formatted out
       [(vu/escape-error-string (format "[%s] is not a valid URL" value))]})))

(def urls-validation
  {:URL url-validation})

(def related-url-validations
  [{:URL url-validation}
   related-url-type-validation])

(def contact-information-url-validation
  {:ContactInformation {:RelatedUrls (v/every related-url-validations)}})

(def data-center-url-validation
  [contact-information-url-validation
   {:ContactPersons (v/every contact-information-url-validation)
    :ContactGroups (v/every contact-information-url-validation)}])
