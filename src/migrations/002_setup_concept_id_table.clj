(ns migrations.002-setup-concept-id-table
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 2."
  []
  (println "migrations.002-setup-concept-id-table up...")
  (j/db-do-commands config/db "CREATE TABLE METADATA_DB.concept_id (
                               sequence_number INTEGER NOT NULL,
                               concept_type VARCHAR(255) NOT NULL,
                               native_id VARCHAR(255) NOT NULL,
                               provider_id VARCHAR(255) NOT NULL)")
  
  (j/db-do-commands config/db "CREATE INDEX index_cpt_nid_pid on METADATA_DB.concept_id
                              (concept_type, native_id, provider_id)"))

(defn down
  "Migrates the database down from version 2."
  []
  (println "migrations.002-setup-concept-id-table down...")
  (j/db-do-commands config/db "DROP TABLE METADATA_DB.concept_id"))