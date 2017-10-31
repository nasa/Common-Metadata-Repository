(ns migrations.009-update-bulk-status-message
  (:require
   [clojure.java.jdbc :as j]
   [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 9."
  []
  (println "migrations.009-update-bulk-status-message up...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE bulk_update_coll_status MODIFY
                     (status_message VARCHAR(4000))"))

(defn down
  "Migrates the database down to version 8."
  []
  (println "migrations.009-update-bulk-status-message down...")
  (j/db-do-commands (config/db) "TRUNCATE TABLE bulk_update_coll_status")
  (j/db-do-commands (config/db) "ALTER TABLE bulk_update_coll_status MODIFY
                                 (status_message VARCHAR(255))"))
