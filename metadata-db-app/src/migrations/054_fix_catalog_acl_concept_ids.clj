



(ns migrations.054-remove-provider-services-tables
  (:require
    [cmr.metadata-db.data.oracle.concept-tables :as ct]
    [config.mdb-migrate-helper :as h]
    [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 54."
  []
  (println "migrations.054-remove-provider-services-tables up...")
  (doseq [provider (h/get-regular-providers)
          :let [t (ct/get-table-name provider :service)
                sequence-name (str t "_seq")]]
    (h/sql (format "drop table %s" t))
    (h/sql (format "drop sequence %s" sequence-name)))
  (h/sql "drop table small_prov_services")
  (h/sql "drop sequence small_prov_services_seq"))

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
    user_id VARCHAR(30) NULL,
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


(defn down
  "Migrates the database down from version 54."
  []
  (println "migrations.054-remove-provider-services-tables down...")
  (doseq [provider (h/get-regular-providers)]
    (ct/create-concept-table (config/db) provider :service)
    (ct/create-concept-table-id-sequence (config/db) provider :service))
  (h/sql create-service-sql)
  (h/sql "CREATE SEQUENCE small_prov_services_seq"))
