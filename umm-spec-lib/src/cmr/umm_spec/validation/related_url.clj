(ns cmr.umm-spec.validation.related-url
  "Defines validations for Related Urls throughout UMM collections."
  (:require
   [clojure.string :as str]
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.validation.umm-spec-validation-utils :as vu])
  (:import
   (org.apache.commons.validator.routines UrlValidator)))

(defn- url-validation
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
  {:URLs (v/every url-validation)})

(def contact-information-url-validation
  {:ContactInformation {:RelatedUrls (v/every urls-validation)}})

(def data-center-url-validation
  [contact-information-url-validation
   {:ContactPersons (v/every contact-information-url-validation)
    :ContactGroups (v/every contact-information-url-validation)}])
