(ns cmr.metadata-db.migrations.004-drop-concept-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 4."
  []
  (println "cmr.metadata-db.migrations.004-drop-concept-table up...")
  (try
    (h/sql "DROP TABLE METADATA_DB.concept")
    (catch Exception e)))

(defn down
  "Migrates the database down from version 4."
  []
  (println "cmr.metadata-db.migrations.004-drop-concept-table down...")
  (println "`down` does nothing for this migration."))