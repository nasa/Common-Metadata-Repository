(ns cmr.metadata-db.migrations.055-remove-provider-services-tables
  (:require
    [config.mdb-migrate-helper :as h]
    [config.mdb-migrate-config :as config]))

(defn- get-services-table-name
  "Returns a provider specific services table-name. Needed in this migration because the
  concept-tables function which does the same has a different behavior after this migration
  was initially written."
  [provider]
  (format "%s_SERVICES" provider))

(defn- get-services-sequence-name
  "Returns a provider specific services sequence name."
  [provider]
  (format "%s_SERVICES_SEQ" provider))

(defn up
  "Migrates the database up to version 55."
  []
  (println "cmr.metadata-db.migrations.055-remove-provider-services-tables up...")
  (doseq [provider (conj (map :provider-id (h/get-regular-providers)) "SMALL_PROV")
          :let [table-name (get-services-table-name provider)
                sequence-name (get-services-sequence-name provider)]]
    (h/sql (format "drop table %s" table-name))
    (h/sql (format "drop sequence %s" sequence-name))))

(defn- create-service-table-for-provider-sql
  "Returns the SQL to create a service table for a provider."
  [provider]
  (format
   "CREATE TABLE %s_SERVICES (
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
    user_id VARCHAR(30) NULL,
    provider_id VARCHAR(255) NOT NULL,
    CONSTRAINT %s_services_pk PRIMARY KEY (id),
    CONSTRAINT %s_services_con_rev
      UNIQUE (provider_id, native_id, revision_id)
      USING INDEX (create unique index %s_services_ucr_i
                          ON %s_services(provider_id, native_id, revision_id)),
    CONSTRAINT %s_services_cid_rev
      UNIQUE (concept_id, revision_id)
      USING INDEX (create unique index %s_services_cri
                          ON %s_services(concept_id, revision_id)))"
   provider provider provider provider provider provider provider provider))

(defn down
  "Migrates the database down from version 55."
  []
  (println "cmr.metadata-db.migrations.055-remove-provider-services-tables down...")
  (doseq [provider (conj (map :provider-id (h/get-regular-providers)) "SMALL_PROV")
          :let [sequence-name (get-services-sequence-name provider)]]
    (h/sql (create-service-table-for-provider-sql provider))
    (h/sql (format "create sequence %s" sequence-name))))
