(ns migrations.001-setup-concept-table
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 1."
  []
   (j/db-do-commands config/db "CREATE TABLE METADATA_DB.concept (
                               concept_type VARCHAR(255) NOT NULL,
                               native_id VARCHAR(255) NOT NULL,
                               concept_id VARCHAR(255) NOT NULL,
                               provider_id VARCHAR(255) NOT NULL,
                               metadata VARCHAR(4000) NOT NULL,
                               format VARCHAR(255) NOT NULL,
                               revision_id INTEGER DEFAULT 0 NOT NULL,
                               deleted INTEGER DEFAULT 0 NOT NULL)")
  (println "migrations.001-setup-concept-table up..."))

(defn down
  "Migrates the database down from version 1."
  []
  (j/db-do-commands config/db "DROP TABLE METADATA_DB.concept")
  (println "migrations.001-setup-concept-table down..."))