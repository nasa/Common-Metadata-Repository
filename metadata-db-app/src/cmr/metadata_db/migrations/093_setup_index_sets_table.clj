(ns cmr.metadata-db.migrations.093-setup-index-sets-table
  (:require [config.mdb-migrate-helper :as h]))

(def ^:private index-sets-column-sql
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

(def ^:private index-sets-constraint-sql
  (str "CONSTRAINT index_sets_pk PRIMARY KEY (id), "
       ;; Unique constraint on native id and revision id
       "CONSTRAINT index_sets_con_rev UNIQUE (native_id, revision_id)
       USING INDEX (create unique index index_sets_ucr_i ON cmr_index_sets (native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT index_sets_cid_rev UNIQUE (concept_id, revision_id)
       USING INDEX (create unique index index_sets_cri ON cmr_index_sets (concept_id, revision_id))"))

(defn- create-index-sets-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_index_sets (%s, %s)" index-sets-column-sql index-sets-constraint-sql)))


(defn- create-index-sets-indices
  []
  (h/sql "CREATE INDEX index_sets_crdi ON cmr_index_sets (concept_id, revision_id, deleted)"))

(defn- create-index-sets-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_index_sets_seq"))

(defn up
  "Migrates the database up to version 93."
  []
  (println "cmr.metadata-db.migrations.093-setup-index-sets-table up...")
  (create-index-sets-table)
  (create-index-sets-indices)
  (create-index-sets-sequence))

(defn down
  "Migrates the database down from version 93."
  []
  (println "cmr.metadata-db.migrations.093-setup-index-sets-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_index_sets_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_index_sets"))
