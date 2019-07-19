(ns cmr.metadata-db.migrations.050-setup-variable-associations-table
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private variable-assocs-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1500) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  associated_concept_id VARCHAR(255) NOT NULL,
  associated_revision_id INTEGER NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  variable_name varchar(20) NOT NULL,
  user_id VARCHAR(30),
  transaction_id NUMBER DEFAULT 0 NOT NULL")

(def ^:private variable-assocs-constraint-sql
  (str "CONSTRAINT v_assoc_pk PRIMARY KEY (id), "
        ;; Unique constraint on native id and revision id
        "CONSTRAINT v_assoc_con_rev UNIQUE (native_id, revision_id)
         USING INDEX (create unique index v_assoc_ucr_i ON cmr_variable_associations (native_id, revision_id)), "

        ;; Unique constraint on concept id and revision id
        "CONSTRAINT v_assoc_cid_rev UNIQUE (concept_id, revision_id)
         USING INDEX (create unique index v_assoc_cri ON cmr_variable_associations (concept_id, revision_id))"))

(defn- create-variable-associations-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_variable_associations (%s, %s)"
                 variable-assocs-column-sql variable-assocs-constraint-sql)))

(defn- create-variable-associations-indices
  []
  (h/sql "CREATE INDEX v_assoc_crdi ON cmr_variable_associations (concept_id, revision_id, deleted)")
  (h/sql "CREATE INDEX v_assoc_acari ON cmr_variable_associations (associated_concept_id, associated_revision_id)")
  (h/sql "CREATE INDEX v_assoc_vnri ON cmr_variable_associations (variable_name, revision_id)")
  (h/sql "CREATE INDEX v_assoc_vncid ON cmr_variable_associations (variable_name, associated_concept_id)")
  (h/sql "CREATE INDEX v_assoc_vncrid ON cmr_variable_associations (variable_name, associated_concept_id, associated_revision_id)"))

(defn- create-variable-associations-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_variable_associations_seq"))

(defn up
  "Migrates the database up to version 50."
  []
  (println "cmr.metadata-db.migrations.050-setup-variable-associations-table up...")
  (create-variable-associations-table)
  (create-variable-associations-indices)
  (create-variable-associations-sequence))

(defn down
  "Migrates the database down from version 50."
  []
  (println "cmr.metadata-db.migrations.050-setup-variable-associations-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_variable_associations_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_variable_associations"))
