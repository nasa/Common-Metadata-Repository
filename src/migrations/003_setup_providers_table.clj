(ns migrations.003-setup-providers-table
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 3."
  []
  (println "migrations.003-setup-providers-table up...")
  (j/db-do-commands config/db "CREATE TABLE METADATA_DB.providers (
                              provider_id VARCHAR2(10) NOT NULL,
                              CONSTRAINT unique_provider_id 
                              UNIQUE (provider_id)
                              USING INDEX (create unique index provider_id_index on providers(provider_id)))"))

(defn down
  "Migrates the database down from version 3."
  []
  (println "migrations.003-setup-providers-table down...")
  (j/db-do-commands config/db "DROP TABLE METADATA_DB.providers"))