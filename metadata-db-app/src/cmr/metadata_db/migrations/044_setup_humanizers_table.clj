(ns cmr.metadata-db.migrations.044-setup-humanizers-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(def ^:private humanizers-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1030) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  user_id VARCHAR(30),
  transaction_id INTEGER DEFAULT 0 NOT NULL")

(def ^:private humanizers-constraint-sql
  (str "CONSTRAINT humanizers_pk PRIMARY KEY (id), "
       ;; Unique constraint on native id and revision id
       "CONSTRAINT humanizers_con_rev UNIQUE (native_id, revision_id)
       USING INDEX (create unique index humanizers_ucr_i ON cmr_humanizers (native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT humanizers_cid_rev UNIQUE (concept_id, revision_id)
       USING INDEX (create unique index humanizers_cri ON cmr_humanizers (concept_id, revision_id))"))

(defn- create-humanizers-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_humanizers (%s, %s)" humanizers-column-sql humanizers-constraint-sql)))


(defn- create-humanizers-indices
  []
  (h/sql "CREATE INDEX humanizers_crdi ON cmr_humanizers (concept_id, revision_id, deleted)"))

(defn- create-humanizers-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_humanizers_seq"))

(defn up
  "Migrates the database up to version 44."
  []
  (println "cmr.metadata-db.migrations.044-setup-humanizers-table up...")
  (create-humanizers-table)
  (create-humanizers-indices)
  (create-humanizers-sequence))

(defn down
  "Migrates the database down from version 44."
  []
  (println "cmr.metadata-db.migrations.044-setup-humanizers-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_humanizers_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_humanizers"))
