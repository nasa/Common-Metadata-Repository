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
  (format "Platform short name [%s] and long name [%s] was not a valid keyword combination."
          (:short-name platform) (:long-name platform)))

(defn instrument-not-matches-kms-keywords
  [instrument]
  (format "Instrument short name [%s] and long name [%s] was not a valid keyword combination."
          (:short-name instrument) (:long-name instrument)))

(defn project-not-matches-kms-keywords
  [project-map]
  (format "Project short name [%s] and long name [%s] was not a valid keyword combination."
          (:short-name project-map) (:long-name project-map)))

(def science-keyword-attribute-order
  "The order of fields that should be displayed in the science keyword human readable list."
  [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3])

(defn- science-keyword->human-attrib-list
  "Converts a science keyword into a human readable list of attributes with their values."
  [sk]
  (let [human-id-values (keep (fn [field]
                                (when-let [value (get sk field)]
                                  (str (vc/humanize-field field) " [" value "]")))
                              science-keyword-attribute-order)]
    (case (count human-id-values)
      1 (first human-id-values)
      2 (str/join " and " human-id-values)
      (str (str/join ", " (drop-last human-id-values)) ", and " (last human-id-values)))))

(defn science-keyword-not-matches-kms-keywords
  [sk]
  (format "Science keyword %s was not a valid keyword combination."
          (science-keyword->human-attrib-list sk)))