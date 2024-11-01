(ns cmr.metadata-db.migrations.091-update-cmr-sub-notifications-table
  "Add a column to the table to store AWS subscription arns."
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 91."
  []
  (println "cmr.metadata-db.migrations.091-update-cmr-sub-notifications-table up...")
  (h/sql "ALTER TABLE METADATA_DB.CMR_SUB_NOTIFICATIONS ADD AWS_ARN VARCHAR(2048) NULL")
  (h/sql "ALTER TABLE METADATA_DB.CMR_SUB_NOTIFICATIONS MODIFY LAST_NOTIFIED_AT TIMESTAMP WITH TIME ZONE NULL"))

(defn down
  "Migrates the database down from version 91."
  []
  (println "cmr.metadata-db.migrations.091-update-cmr-sub-notifications-table down.")
  (h/sql "ALTER TABLE METADATA_DB.CMR_SUB_NOTIFICATIONS DROP COLUMN AWS_ARN"))
  ;; When the database has null values, we cannot make them null so the column will not change back.
