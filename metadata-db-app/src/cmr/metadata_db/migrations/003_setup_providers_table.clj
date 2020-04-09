(ns cmr.metadata-db.migrations.003-setup-providers-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 3."
  []
  (println "cmr.metadata-db.migrations.003-setup-providers-table up...")
  (h/sql "CREATE TABLE METADATA_DB.providers (
                              provider_id VARCHAR2(10) NOT NULL,
                              CONSTRAINT unique_provider_id
                              UNIQUE (provider_id)
                              USING INDEX (create unique index provider_id_index on providers(provider_id)))"))

(defn down
  "Migrates the database down from version 3."
  []
  (println "cmr.metadata-db.migrations.003-setup-providers-table down...")
  (h/sql "DROP TABLE METADATA_DB.providers"))