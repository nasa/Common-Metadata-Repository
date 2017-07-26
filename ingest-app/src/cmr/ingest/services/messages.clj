(ns cmr.ingest.services.messages
  (:require [cmr.common.util :as util]
            [cmr.common.validations.core :as vc]
            [clojure.string :as str]))

(defn parent-collection-does-not-exist
  [provider-id granule-ur collection-ref]
  (let [collection-ref-fields (util/remove-nil-keys (into {} collection-ref))
        coll-ref-humanized-fields (for [[field value] collection-ref-fields]
                                    (format "%s [%s]" (vc/humanize-field field) value))]
    (format "Collection with %s referenced in granule [%s] provider [%s] does not exist."
            (str/join ", " coll-ref-humanized-fields)
            granule-ur provider-id)))

(defn invalid-multipart-params
  [expected-params actual-params]
  (format "Unexpected multipart parameters: [%s]. Expected the multipart parameters [%s]."
          (str/join ", " actual-params)
          (str/join ", " expected-params)))

(defn invalid-revision-id
  [revision-id]
  (format "Invalid revision-id [%s]. Cmr-Revision-id in the header must be a positive integer." revision-id))

(defn invalid-parent-collection-for-validation
  [collection-validation-error]
  (str "The collection given for validating the granule was invalid: " collection-validation-error))

(defn platform-not-matches-kms-keywords
  [platform]
  (format "Platform short name [%s], long name [%s], and type [%s] was not a valid keyword combination."
          (:ShortName platform) (:LongName platform) (:Type platform)))

(defn instrument-not-matches-kms-keywords
  [instrument]
  (format "Instrument short name [%s] and long name [%s] was not a valid keyword combination."
          (:ShortName instrument) (:LongName instrument)))

(defn project-not-matches-kms-keywords
  [project-map]
  (format "Project short name [%s] and long name [%s] was not a valid keyword combination."
          (:ShortName project-map) (:LongName project-map)))

(defn data-center-not-matches-kms-keywords
   "Error msg when DataCenter's ShortName is not in the KMS."
   [data-center]
   (format "Data center short name [%s] was not a valid keyword."
          (:ShortName data-center)))

(defn directory-name-not-matches-kms-keywords
   "Error msg when DirectoryName's ShortName is not in the KMS."
   [directory-name]
   (format "Directory name short name [%s] was not a valid keyword."
          (:ShortName directory-name)))

(defn iso-topic-category-not-matches-kms-keywords
   "Error msg when ISOTopicCategory is not in the KMS."
   [iso-topic-category]
   (format "ISO Topic Category [%s] was not a valid keyword."
           iso-topic-category))

(def science-keyword-attribute-order
  "The order of fields that should be displayed in the science keyword human readable list."
  [:Category :Topic :Term :VariableLevel1 :VariableLevel2 :VariableLevel3])

(def location-keyword-attribute-order
  "The order of fields that should be displayed in the spatial keyword human readable list."
  [:Category :Type :Subregion1 :Subregion2 :Subregion3])

(defn- keyword->human-attrib-list
  "Converts a keyword into a human readable list of attributes with their values."
  [k attribute-order]
  (let [human-id-values (keep (fn [field]
                                (when-let [value (get k field)]
                                  (str (vc/humanize-field field) " [" value "]")))
                              attribute-order)]
    (case (count human-id-values)
      1 (first human-id-values)
      2 (str/join " and " human-id-values)
      ;; else
      (str (str/join ", " (drop-last human-id-values)) ", and " (last human-id-values)))))

(defn science-keyword-not-matches-kms-keywords
  "Create the invalid science keyword message"
  [sk]
  (format "Science keyword %s was not a valid keyword combination."
          (keyword->human-attrib-list sk science-keyword-attribute-order)))

(defn location-keyword-not-matches-kms-keywords
  "Create the invalid location keyword message"
  [lk]
  (format "Location keyword %s was not a valid keyword combination."
          (keyword->human-attrib-list lk location-keyword-attribute-order)))

(def token-required-for-variable-modification
  "Variables cannot be modified without a valid user token.")

(defn variable-deleted
  [native-id]
  (format "Variable with native-id '%s' was deleted." native-id))

(defn variable-does-not-exist
  [native-id]
  (format "Variable could not be found with native-id '%s'" native-id))

(defn variable-already-exists
  [variable concept-id]
  (format "A variable with native-id '%s' already exists with concept id '%s'."
          (:native-id variable)
          concept-id))

(def token-required-for-service-modification
  "Services cannot be modified without a valid user token.")

(defn service-deleted
  [service-name]
  (format "Service with service-name '%s' was deleted." service-name))

(defn service-does-not-exist
  [service-name]
  (format "Service could not be found with service-name '%s'" service-name))

(defn service-already-exists
  [service concept-id]
  (format "A service with native-id '%s' already exists with concept id '%s'."
          (:native-id service)
          concept-id))
