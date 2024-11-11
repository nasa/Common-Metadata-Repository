(ns cmr.metadata-db.migrations.092-update-cmr-subscriptions-table
  "Move the AWS subscription arn column from the cmr_sub_notifications table to the cmr_subscriptions table."
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 92."
  []
  (println "cmr.metadata-db.migrations.092-update-cmr-sub-notifications-table up...")
  (h/sql "ALTER TABLE METADATA_DB.CMR_SUB_NOTIFICATIONS DROP COLUMN AWS_ARN")
  (h/sql "ALTER TABLE METADATA_DB.CMR_SUBSCRIPTIONS ADD AWS_ARN VARCHAR(2048) NULL"))

(defn down
  "Migrates the database down from version 92."
  []
  (println "cmr.metadata-db.migrations.092-update-cmr-sub-notifications-table down.")
  (h/sql "ALTER TABLE METADATA_DB.CMR_SUB_NOTIFICATIONS ADD AWS_ARN VARCHAR(2048) NULL")
  (h/sql "ALTER TABLE METADATA_DB.CMR_SUBSCRIPTIONS DROP COLUMN AWS_ARN"))
