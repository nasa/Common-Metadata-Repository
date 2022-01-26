(ns cmr.metadata-db.migrations.077-remove-email-column
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 77"
  []
  (println "cmr.metadata-db.migrations.077-drop email-column up...")
  (h/sql "alter table METADATA_DB.cmr_subscriptions drop column EMAIL_ADDRESS"))


(defn down
  "Migrates the database down from version 77."
  []
  (println "cmr.metadata-db.migrations.077-drop email-column down...")
  (h/sql "alter table METADATA_DB.cmr_subscriptions add EMAIL_ADDRESS VARCHAR(255) NOT NULL"))