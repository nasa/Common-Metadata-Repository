(ns migrations.004-drop-concept-table
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 4."
  []
  (println "migrations.004-drop-concept-table up...")
  (h/sql "DROP TABLE METADATA_DB.concept"))

(defn down
  "Migrates the database down from version 4."
  []
  (println "migrations.004-drop-concept-table down...")
  (println "`down` does nothing for this migration."))