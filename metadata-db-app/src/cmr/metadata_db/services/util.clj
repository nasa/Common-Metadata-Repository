(ns cmr.metadata-db.services.util
  "Utility methods for concepts."
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.common.util :as util]))

;;; Utility methods
(defn context->db
  [context]
  (-> context
      :system
      :db
      ;; This is a temporary hack to be able to publish event in after-save.
      ;; This will be removed as part of CMR-2520 where we will change cascading delete
      ;; of tag-associations to be asynchronous.
      ;; CMR-2520 Remove the following line.
      (assoc :context context)))

(defn is-tombstone?
  "Check to see if an entry is a tombstone (has a :deleted true entry)."
  [concept]
  (:deleted concept))
