(ns cmr.metadata-db.services.util
  "Utility methods for concepts."
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.common.util :as util]))

;;; Utility methods
(defn context->db
  [context]
  (-> context :system :db))

(defn is-tombstone?
  "Check to see if an entry is a tombstone (has a :deleted true entry)."
  [concept]
  (:deleted concept))

(defn provider-id-length-validation
  [provider-id]
  (when (> (count provider-id) 10)
    [(msg/provider-id-too-long provider-id)]))

(defn provider-id-empty-validation
  [provider-id]
  (when (empty? provider-id)
    [(msg/provider-id-empty provider-id)]))

(defn provider-id-format-validation
  [provider-id]
  (when provider-id
    (when-not (re-matches #"^[a-zA-Z](\w|_)*" provider-id)
      [(msg/invalid-provider-id provider-id)])))

(def provider-id-validation
  "Verify that a provider-id is in the correct form and return a list of errors if not."
  (util/compose-validations [provider-id-length-validation
                             provider-id-empty-validation
                             provider-id-format-validation]))

(def validate-provider-id
  "Validates a provider-id. Throws an error if invalid."
  (util/build-validator :invalid-data provider-id-validation))