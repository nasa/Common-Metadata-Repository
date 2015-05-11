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