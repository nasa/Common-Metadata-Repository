(ns cmr.metadata-db.services.utility
  "Utitly methods for concepts."
  (:require [clojure.string :as string]
            [cmr.common.services.errors :as errors]
            [cmr.metadata-db.services.messages :as messages]))

;;; Constants
(def concept-id-prefix-length 1)

;;; Utility methods
(defn concept-type-prefix
  "Truncate and upcase a concept-type to create a prefix for concept-ids"
  [concept-type-keyword]
  (let [concept-type (name concept-type-keyword)]
    (string/upper-case (subs concept-type 0 (min (count concept-type) concept-id-prefix-length)))))

(defn generate-concept-id
  "Generate a concept-id given concept type, sequence number, and provider id."
  [concept-type provider-id seq-num]
  (str (concept-type-prefix concept-type) seq-num "-" provider-id))

(defn validate-concept-id
  "Validate that the concept-id for a concept matches the concept-type and provider-id."
  [concept]
  (let [{:keys [concept-id concept-type provider-id]} concept
        prefix (concept-type-prefix concept-type)
        pattern (re-pattern (str prefix "\\d*-" provider-id))]
    (re-find pattern concept-id)))

(defn validate-concept
  "Validate that a concept has the fields we need to save it."
  [concept]
  (let [{:keys [concept-id concept-type provider-id native-id]} concept
        error (cond
                (nil? concept-type)
                (messages/missing-concept-type-msg)
                
                (nil? provider-id)
                (messages/missing-provider-id-msg)
                
                (nil? native-id)
                (messages/missing-native-id-msg)
                
                (and concept-id (not (validate-concept-id concept)))
                (messages/invalid-concept-id-msg concept-id provider-id concept-type)
                
                :else nil)]
    (if error (errors/throw-service-error :invalid-data error))))

(defn is-tombstone?
  "Check to see if an entry is a tombstone (has a :deleted true entry)."
  [concept]
  (:deleted concept))