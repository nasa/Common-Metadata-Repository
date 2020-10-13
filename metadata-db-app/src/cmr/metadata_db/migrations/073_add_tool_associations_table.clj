(ns cmr.metadata-db.migrations.073-add-tool-associations-table
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private tool-assocs-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1500) NOT NULL,
  tool_concept_id VARCHAR(255) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  associated_concept_id VARCHAR(255) NOT NULL,
  associated_revision_id INTEGER NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  user_id VARCHAR(30),
  transaction_id NUMBER DEFAULT 0 NOT NULL")

(def ^:private tool-assocs-constraint-sql
  (str "CONSTRAINT t_assoc_pk PRIMARY KEY (id), "
        ;; Unique constraint on native id and revision id
        "CONSTRAINT t_assoc_con_rev UNIQUE (native_id, revision_id)
         USING INDEX (create unique index t_assoc_ucr_i ON CMR_TOOL_ASSOCIATIONS (native_id, revision_id)), "

        ;; Unique constraint on concept id and revision id
        "CONSTRAINT t_assoc_cid_rev UNIQUE (concept_id, revision_id)
         USING INDEX (create unique index t_assoc_cri ON CMR_TOOL_ASSOCIATIONS (concept_id, revision_id))"))

(defn- create-tool-associations-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.CMR_TOOL_ASSOCIATIONS (%s, %s)"
                 tool-assocs-column-sql tool-assocs-constraint-sql)))

(defn- create-tool-associations-indices
  []
  (h/sql "CREATE INDEX t_assoc_crdi ON CMR_TOOL_ASSOCIATIONS (concept_id, revision_id, deleted)")
  (h/sql "CREATE INDEX t_assoc_acari ON CMR_TOOL_ASSOCIATIONS (associated_concept_id, associated_revision_id)")
  (h/sql "CREATE INDEX t_assoc_tcid ON CMR_TOOL_ASSOCIATIONS (tool_concept_id, revision_id)")
  (h/sql "CREATE INDEX t_assoc_tcarid ON CMR_TOOL_ASSOCIATIONS (tool_concept_id, associated_concept_id, associated_revision_id)"))

(defn- create-tool-associations-sequence
  []
  (h/sql "CREATE SEQUENCE CMR_TOOL_ASSOCIATIONS_SEQ"))

(defn up
  "Migrates the database up to version 73."
  []
  (println "cmr.metadata-db.migrations.073-add-tool-associations-table up...")
  (create-tool-associations-table)
  (create-tool-associations-indices)
  (create-tool-associations-sequence))

(defn down
  "Migrates the database down from version 73."
  []
  (println "cmr.metadata-db.migrations.073-add-tool-associations-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.CMR_TOOL_ASSOCIATIONS_SEQ")
  (h/sql "DROP TABLE METADATA_DB.CMR_TOOL_ASSOCIATIONS"))
