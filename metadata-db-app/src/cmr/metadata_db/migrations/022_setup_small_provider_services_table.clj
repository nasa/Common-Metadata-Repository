(ns cmr.metadata-db.migrations.022-setup-small-provider-services-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))


(def create-service-sql
  "CREATE TABLE small_prov_services (
    id NUMBER,
    concept_id VARCHAR(255) NOT NULL,
    native_id VARCHAR(1030) NOT NULL,
    metadata BLOB NOT NULL,
    format VARCHAR(255) NOT NULL,
    revision_id INTEGER DEFAULT 1 NOT NULL,
    revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    deleted INTEGER DEFAULT 0 NOT NULL,
    entry_id VARCHAR(255) NOT NULL,
    entry_title VARCHAR(1030) NOT NULL,
    delete_time TIMESTAMP WITH TIME ZONE,
    user_id VARCHAR(30) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    CONSTRAINT small_prov_services_pk PRIMARY KEY (id),
    CONSTRAINT small_prov_services_con_rev
      UNIQUE (provider_id, native_id, revision_id)
      USING INDEX (create unique index small_prov_services_ucr_i
                          ON small_prov_services(provider_id, native_id, revision_id)),
    CONSTRAINT small_prov_services_cid_rev
      UNIQUE (concept_id, revision_id)
      USING INDEX (create unique index small_prov_services_cri
                          ON small_prov_services(concept_id, revision_id)))")

(def create-serv-seq-sql "CREATE SEQUENCE small_prov_services_seq")

(defn up
  "Migrates the database up to version 22."
  []
  (println "cmr.metadata-db.migrations.022-setup-small-provider-services-table up...")
  ;; Create the SMALL_PROV_services table and sequence
  (h/sql create-service-sql)
  (h/sql create-serv-seq-sql))

(defn down
  "Migrates the database down from version 22."
  []
  (println "cmr.metadata-db.migrations.022-setup-small-provider-services-table down...")
  (h/sql "drop table small_prov_services")
  (h/sql "drop sequence small_prov_services_seq"))