(ns cmr.metadata-db.migrations.072-add-last-notification-to-subscriptions-table
  "Adds time of last notification to subscription table"
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 72."
  []
  (println "cmr.metadata-db.migrations.072-add-last-notification-to-subscriptions-table up...")
  (h/sql (str "ALTER TABLE cmr_subscriptions "
   "ADD last_notification_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL")))

(defn down
  "Migrates the database down from version 72."
  []
  (println "cmr.metadata-db.migrations.072-add-last-notification-to-subscriptions-table down.")
  (h/sql "ALTER TABLE cmr_subscriptions DROP COLUMN last_notification_at"))
