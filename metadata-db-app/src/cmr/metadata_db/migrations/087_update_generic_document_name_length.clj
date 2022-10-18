(ns cmr.metadata-db.migrations.087-update-generic-document-name-length
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 87."
  []
  (println "cmr.metadata-db.migrations.087-update-generic-document-name-length up...")
  (h/sql "alter table METADATA_DB.CMR_GENERIC_DOCUMENTS modify document_name VARCHAR(1020)"))


(defn down
  "Migrates the database down from version 87."
  []
  ;; do nothing as we don't want to truncate the document name
  (println "cmr.metadata-db.migrations.087-update-generic-document-name-length down..."))
