(ns cmr.umm-spec.validation.related-url
  "Defines validations for Related Urls throughout UMM collections."
  (:require
   [clojure.string :as str]
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.validation.umm-spec-validation-utils :as vu])
  (:import
   (org.apache.commons.validator.routines UrlValidator)))

(defn- valid-url-content-types-combo?
  "Returns true if valid Type and Subtype for URLContentType, nil otherwise."
  [url-content-type type sub-type]
  (when (and (some #(= type %) (su/valid-types-for-url-content-type url-content-type))
             (or (nil? sub-type)
                 (some #(= sub-type %) (su/valid-subtypes-for-type url-content-type type))))
    true))

(defn- related-url-type-validation
  "Validate the Type and Subtype being valid for the accompanying URLContentType"
  [field-path value]
  (let [{:keys [URLContentType Type Subtype]} value]
    (when-not (valid-url-content-types-combo? URLContentType Type Subtype)
      {field-path
       [(vu/escape-error-string (format "URLContentType: %s, Type: %s, Subtype: %s is not a vaild URLContentType/Type/Subtype combination."
                                        URLContentType Type Subtype))]})))

(defn- data-center-url-content-type-validation
  "Validates the URLContentType for DataCenter ContactInformation"
  [field-path value]
  (when-not (= "DataCenterURL" (:URLContentType value))
    {field-path
     [(vu/escape-error-string (format "URLContentType must be DataCenterURL for DataCenter RelatedUrls"))]}))

(defn- data-center-contact-url-content-type-validation
  "Validates the URLContentType for DataCenter ContactGroups and ContactPersons ContactInformation"
  [field-path value]
  (when-not (= "DataContactURL" (:URLContentType value))
    {field-path
     [(vu/escape-error-string (format "URLContentType must be DataContactURL for ContactPersons or ContactGroups RelatedUrls"))]}))

(defn- get-service-validation
  "Validates that DistributionURL URLContentType with GET SERVICE types are the only
  RelatedUrls that contain GetService"
  [field-path value]
  (let [{:keys [URLContentType Type GetService]} value]
    (when (and (not= "DistributionURL" URLContentType)
               (not= "GET Service" Type)
               GetService)
      {field-path
       [(vu/escape-error-string (format "Only URLContentType: DistributionURL Type: GET SERVICE can contain GetService, RelatedUrl contains URLContentType: %s Type: %s" URLContentType Type))]})))

(defn- get-data-validation
  "Validates that DistributionURL URLContentType with GET DATA types are the only
  RelatedUrls that contain GetData"
  [field-path value]
  (let [{:keys [URLContentType Type GetData]} value]
    (when (and (not= "DistributionURL" URLContentType)
               (not= "GET DATA" Type)
               GetData)
      {field-path
       [(vu/escape-error-string (format "Only URLContentType: DistributionURL Type: GET DATA can contain GetData, RelatedUrl contains URLContentType: %s Type: %s" URLContentType Type))]})))

(defn url-validation
  "Validate the URL. Return nil if no errors and the field path and error if the URL
  is not valid."
  [field-path value]
  (let [validator (UrlValidator. (into-array ["http" "ftp" "https" "file" "s3"]))]
    (when (and (some? value)
               (not= value su/not-provided-url)
               (not (.isValid validator value)))
      {field-path
       ;; Escape the %, because the error messages go through a format, which will throw an error
       ;; Do the escape after the format here, so it doesn't get formatted out
       [(vu/escape-error-string (format "[%s] is not a valid URL" value))]})))

(defn s3-bucket-validation
  "Validate the S3 bucket url or prefix. Returns the field path and error if invalid, otherwise nil.
   UrlValidator requires an extension e.g. .com, which many s3 links do not have, see [[url-validation]]"
  [field-path value]
  (let [url-pattern (re-pattern "^[\\w\\d]+:\\/\\/")]
    (when (or (and (re-seq url-pattern value)
                   (not (str/starts-with? value "s3")))
              (= su/not-provided-url value)
              (re-seq #"[\[\"\s,;]" value))
      {field-path
       [(vu/escape-error-string (format "[%s] is not a valid S3 bucket or prefix" value))]})))

(defn description-validation
  "Validation that checks if description exists, when the URL is specified"
  [field-path value]
  (let [{:keys [URL Description]} value]
    (when (and (some? URL)
               (not= URL su/not-provided-url)
               (not (seq Description)))
      {field-path
       [(vu/escape-error-string "RelatedUrl does not have a description.")]})))

(def urls-validation
  {:URL url-validation})

(def related-url-validations
  [{:URL url-validation}
   description-validation
   get-service-validation
   get-data-validation])

(def contact-persons-groups-contact-information-validations
  {:ContactInformation {:RelatedUrls [(v/every related-url-validations)
                                      (v/every data-center-contact-url-content-type-validation)]}})

(def data-center-contact-information-url-validations
  {:ContactInformation {:RelatedUrls [(v/every related-url-validations)
                                      (v/every data-center-url-content-type-validation)]}})

(def data-center-url-validation
  [data-center-contact-information-url-validations
   {:ContactPersons (v/every contact-persons-groups-contact-information-validations)
    :ContactGroups (v/every contact-persons-groups-contact-information-validations)}])
