(ns cmr.metadata-db.migrations.040-create-acls-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(def ^:private acls-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1030) NOT NULL,
  provider_id VARCHAR(10),
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  user_id VARCHAR(30)")

(def ^:private acls-constraint-sql
  (str "CONSTRAINT cmr_acls_pk PRIMARY KEY (id), "
       ;; Unique constraint on provider-id, native id and revision id
       "CONSTRAINT cmr_acls_pnid_rev UNIQUE (provider_id, native_id, revision_id)
       USING INDEX (create unique index cmr_acls_nr_i ON cmr_acls (provider_id, native_id, revision_id)), "

       ;; Unique constraint on provider-id, concept id and revision id
       "CONSTRAINT cmr_acls_pcid_rev UNIQUE (provider_id, concept_id, revision_id)
       USING INDEX (create unique index cmr_acls_cr_i ON cmr_acls (provider_id, concept_id, revision_id))"))

(defn- create-acls-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_acls (%s, %s)" acls-column-sql acls-constraint-sql)))


(defn- create-acls-indices
  []
  (h/sql "CREATE INDEX cmr_acls_crdi ON cmr_acls (concept_id, revision_id, deleted)"))

(defn- create-acls-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_acls_seq"))

(defn up
  "Migrates the database up to version 40."
  []
  (println "cmr.metadata-db.migrations.040-create-acls-table up...")
  (create-acls-table)
  (create-acls-indices)
  (create-acls-sequence))

(defn down
  "Migrates the database down from version 40."
  []
  (println "cmr.metadata-db.migrations.040-create-acls-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_acls_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_acls"))