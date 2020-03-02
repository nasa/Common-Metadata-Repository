(ns cmr.ingest.migrations.002-create-provider-acl-hash-table
  (:require [clojure.java.jdbc :as j]
            [config.ingest-migrate-config :as config]))

(defn up
  "Migrates the database up to version 2."
  []
  (println "cmr.ingest.migrations.002-create-provider-acl-hash-table up...")
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.provider_acl_hash (
                              acl_hashes VARCHAR2(2000) NOT NULL)"))

(defn down
  "Migrates the database down from version 2."
  []
  (println "cmr.ingest.migrations.002-create-provider-acl-hash-table down...")
  (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.provider_acl_hash"))
