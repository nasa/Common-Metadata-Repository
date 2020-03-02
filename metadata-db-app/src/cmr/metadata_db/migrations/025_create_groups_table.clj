(ns cmr.metadata-db.migrations.025-create-groups-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(def ^:private groups-column-sql
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

(def ^:private groups-constraint-sql
  (str "CONSTRAINT cmr_groups_pk PRIMARY KEY (id), "
       ;; Unique constraint on provider-id, native id and revision id
       "CONSTRAINT cmr_groups_pnid_rev UNIQUE (provider_id, native_id, revision_id)
       USING INDEX (create unique index cmr_groups_nr_i ON cmr_groups (provider_id, native_id, revision_id)), "

       ;; Unique constraint on provider-id, concept id and revision id
       "CONSTRAINT cmr_groups_pcid_rev UNIQUE (provider_id, concept_id, revision_id)
       USING INDEX (create unique index cmr_groups_cr_i ON cmr_groups (provider_id, concept_id, revision_id))"))

(defn- create-groups-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_groups (%s, %s)" groups-column-sql groups-constraint-sql)))


(defn- create-groups-indices
  []
  (h/sql "CREATE INDEX cmr_groups_crdi ON cmr_groups (concept_id, revision_id, deleted)"))

(defn- create-groups-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_groups_seq"))

(defn up
  "Migrates the database up to version 25."
  []
  (println "cmr.metadata-db.migrations.025-setup-groups-table up...")
  (create-groups-table)
  (create-groups-indices)
  (create-groups-sequence))

(defn down
  "Migrates the database down from version 25."
  []
  (println "cmr.metadata-db.migrations.025-setup-groups-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_groups_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_groups"))