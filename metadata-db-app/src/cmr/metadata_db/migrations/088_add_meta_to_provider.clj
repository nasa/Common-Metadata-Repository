(ns cmr.metadata-db.migrations.088-add-meta-to-provider
  (:require
   [config.mdb-migrate-helper :as mh]))

(defn up
  "Migrates the database up to version 88."
  []
  (println "cmr.metadata-db.migrations.088-add-meta-to-provider up...")
  (mh/sql "alter table METADATA_DB.PROVIDERS ADD metadata BLOB"))


(defn down
  "Migrates the database down from version 88."
  []
  ;; do nothing as we don't want to loose the metadata documents
  (println "cmr.metadata-db.migrations.087-update-generic-document-name-length down..."))
