(ns cmr.indexer.data.concepts.collection.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require
    [clojure.string :as string]
    [cmr.common-app.services.kms-fetcher :as kf]
    [cmr.common-app.services.kms-lookup :as kms-lookup]
    [cmr.common.util :as util]))

;;
;; *****************************************************************************
;; version 2 platforms

(def default-platform-values
  "Default values to use for any platform fields which are nil. Note: short-name
   should not be nil as it is the lookup key."
  (zipmap [:basis :category :sub-category :long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn platform2-nested-fields->elastic-doc
  "Converts a platform short-name into an elastic document with the full nested
   hierarchy for that short-name from the GCMD KMS keywords. If a field is not
   present in the KMS hierarchy, we use a dummy value to indicate the field was
   not present."
  [kms-index short-name]
  (let [full-platform
        (kms-lookup/lookup-by-short-name kms-index :platforms short-name)
        {:keys [basis category sub-category short-name long-name uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name}} full-platform]
    {:basis basis
     :basis-lowercase (util/safe-lowercase basis)
     :category category
     :category-lowercase (util/safe-lowercase category)
     :sub-category sub-category
     :sub-category-lowercase (util/safe-lowercase sub-category)
     :short-name short-name
     :short-name-lowercase (util/safe-lowercase short-name)
     :long-name long-name
     :long-name-lowercase (util/safe-lowercase long-name)
     :uuid uuid
     :uuid-lowercase (util/safe-lowercase uuid)}))

(defn humanized-platform2-nested-fields->elastic-doc
  "Extracts humanized fields from the platform short name, then use KMS to look up the rest of the
  values and places them into an elastic doc with the same shape/keys as
  platform2-nested-fields->elastic-doc.  If the humanized short-name does not exist in KMS then
  look up the original short name and fill out the doc if it exists. If it does exist just replace
  the original short name with the humanized one.  If it doesn't then just return the humanized
  platform."
  [kms-index platform]
  (let [humanized-fields (filter #(-> % key namespace (= "cmr-humanized")) platform)
        humanized-fields-with-raw-values (util/map-values :value humanized-fields)
        ns-stripped-fields (util/map-keys->kebab-case humanized-fields-with-raw-values)
        humanized-platform (platform2-nested-fields->elastic-doc kms-index
                                                                 (:short-name ns-stripped-fields))]
    (if (:basis humanized-platform)
      humanized-platform
      (let [original-field (:ShortName platform)
            original-platform (platform2-nested-fields->elastic-doc kms-index original-field)]
        (if (:basis original-platform)
          (assoc original-platform :short-name (:short-name humanized-platform)
                                   :short-name-lowercase (:short-name-lowercase humanized-platform))
          humanized-platform)))))

;; *****************************************************************************
;; version 1 platforms

;DEPRECATED
(defn platform-short-name->elastic-doc
  "Converts a platform short-name into an elastic document with the full nested
   hierarchy for that short-name from the GCMD KMS keywords. If a field is not
   present in the KMS hierarchy, we use a dummy value to indicate the field was
   not present."
  [kms-index short-name]
  (let [full-platform
        (merge default-platform-values
               (kms-lookup/lookup-by-short-name kms-index :platforms short-name))
        {:keys [basis category sub-category short-name long-name uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name}} full-platform]
    {:category category
     :category-lowercase (string/lower-case category)
     :series-entity sub-category
     :series-entity-lowercase (string/lower-case sub-category)
     :short-name short-name
     :short-name-lowercase (string/lower-case short-name)
     :long-name long-name
     :long-name-lowercase (string/lower-case long-name)
     :uuid uuid
     :uuid-lowercase (util/safe-lowercase uuid)}))
