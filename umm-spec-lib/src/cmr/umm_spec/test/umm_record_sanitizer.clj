(ns cmr.umm-spec.test.umm-record-sanitizer
  "This contains functions for manipulating the generator generated umm-record to a sanitized
  version to pass the xml validation for various supported metadata format. This is needed because
  the incompatibility between UMM JSON schema and schemas of the various metadata formats making the
  generated metadata xml invalid without some kind of sanitization."
  (:require
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as test]
   [cmr.common.util :as util :refer [update-in-each]]))

(defn- set-if-exist
  "Sets the field of the given record to the value if the field has a value, returns the record."
  [record field value]
  (if (field record)
    (assoc record field value)
    record))

(defn- sanitize-science-keywords
  "Temporary! We should be able to define the JSON schema in a way that ensures science keyword
  hierarchy is obeyed. It could potentially be done using a complex oneOf or anyOf."
  [record]
  (assoc record
         :ScienceKeywords (seq (for [sk (:ScienceKeywords record)]
                                 (cond
                                   (nil? (:VariableLevel1 sk))
                                   (assoc sk
                                          :VariableLevel2 nil
                                          :VariableLevel3 nil
                                          :DetailedVariable nil)

                                   (nil? (:VariableLevel2 sk))
                                   (assoc sk :VariableLevel3 nil)

                                   :else
                                   sk)))))


(defn- sanitize-related-url
  "Returns a single sanitized RelatedUrl"
  [entry]
  (if-let [urls (get-in entry [:RelatedUrl :URL])]
    (assoc-in entry [:RelatedUrl :URL] (gen/sample test/file-url-string 1))
    entry))

(defn- sanitize-related-urls
  "Returns a list of sanitized related urls"
  [related-urls]
  (when related-urls
   (for [ru related-urls]
    (assoc ru :URL (first (gen/sample test/file-url-string 1))))))

(defn- sanitize-contact-informations
  "Sanitize a record with ContactInformation"
  [records]
  (seq (for [record records]
         (if-let [contact-info (:ContactInformation record)]
           (update-in record [:ContactInformation :RelatedUrls] sanitize-related-urls)
           record))))

(defn- sanitize-data-centers
  "Sanitize data center contact information and the contact information on contact
  persons and groups associated with the data center"
  [data-centers]
  (seq (for [dc (sanitize-contact-informations data-centers)]
         (-> dc
             (update :ContactPersons sanitize-contact-informations)
             (update :ContactGroups sanitize-contact-informations)))))

(defn- sanitize-umm-record-related-urls
  "Sanitize the RelatedUrls on the collection if any exist"
  [record]
  (if (seq (:RelatedUrls record))
    (update record :RelatedUrls sanitize-related-urls)
    record))

(defn- sanitize-umm-record-data-centers
  "Sanitize data centers if there are any data centers in the record"
  [record]
  (if (seq (:DataCenters record))
    (update record :DataCenters sanitize-data-centers)
    record))

(defn- sanitize-umm-record-contacts
  "Sanitize RelatedUrls if they key exists."
  [record key]
  (if (seq (get record key))
    (update record key sanitize-contact-informations)
    record))

(defn- sanitize-umm-record-related-url
  "Sanitize a single RelatedUrl in a collection if they key exists."
  [collection key]
  (if (seq (get collection key))
    (update-in-each collection [key] sanitize-related-url)
    collection))

(defn- sanitize-online-resource
  "Sanitize the OnlineResource Linkage (url)"
  [c]
  (if (get-in c [:OnlineResource :Linkage])
   (assoc-in c [:OnlineResource :Linkage] (first (gen/sample test/file-url-string 1)))
   c))

(defn- sanitize-umm-record-urls
  "Sanitize all RelatedUrls throughout the collection by making them url strings."
  [record]
  (-> record
      sanitize-umm-record-related-urls
      sanitize-umm-record-data-centers
      (sanitize-umm-record-contacts :ContactPersons)
      (sanitize-umm-record-contacts :ContactGroups)
      (sanitize-umm-record-related-url :CollectionCitations)
      (sanitize-umm-record-related-url :PublicationReferences)
      (update-in-each [:PublicationReferences] sanitize-online-resource)))

(defn sanitized-umm-record
  "Returns the sanitized version of the given umm record."
  [record]
  (-> record
      ;; DataLanguage should be from a list of enumerations which are not defined in UMM JSON schema
      ;; so here we just replace the generated value to eng to make it through the validation.
      (set-if-exist :DataLanguage "eng")
      (set-if-exist :CollectionProgress "COMPLETE")

      ;; Figure out if we can define this in the schema
      sanitize-science-keywords
      sanitize-umm-record-urls))
