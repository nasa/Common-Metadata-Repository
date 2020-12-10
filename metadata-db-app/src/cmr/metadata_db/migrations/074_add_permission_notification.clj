(ns cmr.metadata-db.migrations.074-add-permission-notification
  "Adds fingerprint to variables table"
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 74."
  []
  (println "cmr.metadata-db.migrations.074-add-permission-notification up...")
  (h/sql "alter table METADATA_DB.cmr_sub_notifications add permission_check_time TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL")
  (h/sql "alter table METADATA_DB.cmr_sub_notifications add permission_check_failed INTEGER DEFAULT 0 NOT NULL"))

(defn down
  "Migrates the database down from version 74."
  []
  (println "cmr.metadata-db.migrations.074-add-permission-notification down.")
  (h/sql "alter table METADATA_DB.cmr_sub_notifications drop column permission_check_time")
  (h/sql "alter table METADATA_DB.cmr_sub_notifications drop column permission_check_failed"))
