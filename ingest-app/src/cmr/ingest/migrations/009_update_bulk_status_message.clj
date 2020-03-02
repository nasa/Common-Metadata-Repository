(ns cmr.ingest.migrations.009-update-bulk-status-message
  (:require
   [clojure.java.jdbc :as j]
   [config.ingest-migrate-config :as config]))

(defn up
  "Migrates the database up to version 9."
  []
  (println "cmr.ingest.migrations.009-update-bulk-status-message up...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE bulk_update_coll_status MODIFY
                     (status_message VARCHAR2(4000))"))

(defn down
  "Migrates the database down to version 8."
  []
  (println "cmr.ingest.migrations.009-update-bulk-status-message down..."))
