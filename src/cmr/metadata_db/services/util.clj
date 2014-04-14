(ns cmr.metadata-db.services.util
  "Utility methods for concepts."
  (:require [cmr.metadata-db.services.messages :as msg]))

;;; Utility methods
(defn context->db
  [context]
  (-> context :system :db))

(defn is-tombstone?
  "Check to see if an entry is a tombstone (has a :deleted true entry)."
  [concept]
  (:deleted concept))

(defn validate-provider-id
  "Verify that a provider-id is in the correct format."
  [provider-id]
  (when-let [error-message (cond
                             (> (count provider-id) 10)
                             msg/provider-id-too-long

                             (empty? provider-id)
                             msg/provider-id-empty

                             (not (re-matches #"^[a-zA-Z](\w|_)*" provider-id))
                             msg/invalid-provider-id)]
    (msg/data-error :invalid-data error-message provider-id)))