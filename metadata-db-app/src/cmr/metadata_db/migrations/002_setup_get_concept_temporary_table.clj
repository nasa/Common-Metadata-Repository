(ns cmr.metadata-db.migrations.002-setup-get-concept-temporary-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 2."
  []
  (println "cmr.metadata-db.migrations.002-setup-get-concept-temporary-table up...")
  (h/sql "CREATE GLOBAL TEMPORARY TABLE METADATA_DB.get_concepts_work_area
                         (concept_id VARCHAR(255),
                         revision_id INTEGER)
                         ON COMMIT DELETE ROWS"))

(defn down
  "Migrates the database down from version 2."
  []
  (println "cmr.metadata-db.migrations.002-setup-get-concept-temporary-table down...")
  (h/sql "DROP TABLE METADATA_DB.get_concepts_work_area"))