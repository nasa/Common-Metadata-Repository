(ns cmr.metadata-db.migrations.071-setup-tools-table
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private tools-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1030) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  user_id VARCHAR(30),
  tool_name VARCHAR(80),
  provider_id VARCHAR(10) NOT NULL,
  transaction_id INTEGER DEFAULT 0 NOT NULL")

(def ^:private tools-constraint-sql
  (str "CONSTRAINT tools_pk PRIMARY KEY (id), "
       ;; Unique constraint on native id and revision id
       "CONSTRAINT tools_con_pnr UNIQUE (provider_id, native_id, revision_id)
       USING INDEX (create unique index tools_idx_pnr ON cmr_tools (provider_id, native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT tools_con_cr UNIQUE (concept_id, revision_id)
       USING INDEX (create unique index tools_idx_cr ON cmr_tools (concept_id, revision_id))"))

(defn- create-tools-table
  []
  (h/sql
   (format "CREATE TABLE METADATA_DB.cmr_tools (%s, %s)"
           tools-column-sql tools-constraint-sql)))


(defn- create-tools-indices
  []
  ;; Supports queries to find tool revisions that are deleted
  (h/sql "CREATE INDEX tools_idx_crd ON METADATA_DB.cmr_tools (concept_id, revision_id, deleted)")
  ;; Supports queries to find tools by tool name
  (h/sql "CREATE INDEX tools_idx_n ON METADATA_DB.cmr_tools (tool_name)"))

(defn- create-tools-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_tools_seq"))

(defn up
  "Migrates the database up to version 71."
  []
  (println "cmr.metadata-db.migrations.071-setup-tools-table up...")
  (create-tools-table)
  (create-tools-indices)
  (create-tools-sequence))

(defn down
  "Migrates the database down from version 71."
  []
  (println "cmr.metadata-db.migrations.071-setup-tools-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_tools_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_tools"))
