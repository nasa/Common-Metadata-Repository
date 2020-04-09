(ns cmr.ingest.migrations.003-update-provider-acl-hash-table
  (:require [clojure.java.jdbc :as j]
            [config.ingest-migrate-config :as config]))

(defn- drop-provider-acl-hash-table
  "Drop the provider_acl_hash table."
  []
  (try
    (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.provider_acl_hash")
    (catch Exception e)))

(defn up
  "Migrates the database up to version 3."
  []
  (println "cmr.ingest.migrations.003-update-provider-acl-hash-table up...")
  ;; Alter table does not work directly for changing from varchar to BLOB,
  ;; so we drop the table and recreate it with the new type since we don't need to preserve the data.
  ;; We're storing the provider acl hash as a compressed value.
  (drop-provider-acl-hash-table)
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.provider_acl_hash (
                                acl_hashes BLOB NOT NULL)"))

(defn down
  "Migrates the database down from version 3."
  []
  (println "cmr.ingest.migrations.003-update-provider-acl-hash-table down...")
  (drop-provider-acl-hash-table)
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.provider_acl_hash (
                                acl_hashes VARCHAR2(2000) NOT NULL)"))
