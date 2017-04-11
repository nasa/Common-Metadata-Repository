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
  "Returns true if valid Type and SubType for URLContentType, nil otherwise."
  [url-content-type type sub-type]
  (when (and (some #(= type %) (su/valid-types-for-url-content-type url-content-type))
             (or (nil? sub-type)
                 (some #(= sub-type %) (su/valid-subtypes-for-type url-content-type type))))
    true))

(defn- related-url-type-validation
  "Validate the Type and SubType being valid for the accompanying URLContentType"
  [field-path value]
  (let [{:keys [URLContentType Type SubType]} value]
    (when-not (valid-url-content-types-combo? URLContentType Type SubType)
      {field-path
       [(vu/escape-error-string (format "URLContentType: %s, Type: %s, SubType: %s is not a vaild URLContentType/Type/SubType combination."
                                        URLContentType Type SubType))]})))

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
  (let [validator (UrlValidator. (into-array ["http" "ftp" "https" "file"]))]
    (when (and (some? value)
               (not= value su/not-provided-url)
               (not (.isValid validator value)))
      {field-path
       ;; Escape the %, because the error messages go through a format, which will throw an error
       ;; Do the escape after the format here, so it doesn't get formatted out
       [(vu/escape-error-string (format "[%s] is not a valid URL" value))]})))

(def urls-validation
  {:URL url-validation})

(def related-url-validations
  [{:URL url-validation}
   related-url-type-validation
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
