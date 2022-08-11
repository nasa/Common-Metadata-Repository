(ns cmr.metadata-db.migrations.084-migrate-tool-associations
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
  "Migrates the database up to version 84."
  []
  (println "cmr.metadata-db.migrations.084-migrate-tool-associations up...")
 
  ;; rename CMR_TOOL_ASSOCIATIONS table to prevent new entries from being inserted.
  (h/sql "ALTER TABLE CMR_TOOL_ASSOCIATIONS RENAME TO CMR_TOOL_ASSOCIATIONS_OLD")

  ;; Migrate tool association data to CMR_ASSOCIATIONS table.
  ;; Note: We will need to use the CMR_ASSOCIATIONS_SEQ.nextval for the ID column to make it unique in the new table.
  (h/sql "INSERT INTO CMR_ASSOCIATIONS (ID,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,SOURCE_CONCEPT_IDENTIFIER,ASSOCIATION_TYPE)
          SELECT CMR_ASSOCIATIONS_SEQ.nextval,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,TOOL_CONCEPT_ID,'TOOL-COLLECTION'
          FROM CMR_TOOL_ASSOCIATIONS_OLD")

  ;; Drop the old table.
  (h/sql "DROP TABLE CMR_TOOL_ASSOCIATIONS_OLD")

  ;; Drop the sequence
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_tool_associations_seq"))

(defn down
  "Migrates the database down from version 84."
  []
  (println "cmr.metadata-db.migrations.084-migrate-tool-associations down...")

  ;; rename CMR_ASSOCIATIONS table to prevent new entries from being inserted.
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS RENAME TO CMR_ASSOCIATIONS_OLD")

 
  ;; Create CMR_TOOL_ASSOCIATIONS table.
  (create-tool-associations-table)
  (create-tool-associations-indices)
  (create-tool-associations-sequence)

  ;; Migrate tool associations back to CMR_TOOL_ASSOCIATIONS table.
  ;; Note: We will now need to use the CMR_TOOL_ASSOCIATIONS_SEQ.nextval for the ID column so that
  ;; when new entries are inserted, all the ids will be unique.
  (h/sql "INSERT INTO CMR_TOOL_ASSOCIATIONS (ID,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,TOOL_CONCEPT_ID)
          SELECT CMR_TOOL_ASSOCIATIONS_SEQ.nextval,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,SOURCE_CONCEPT_IDENTIFIER
          FROM CMR_ASSOCIATIONS_OLD
          WHERE ASSOCIATION_TYPE='TOOL-COLLECTION'")

  ;; Remove all the tool associations migrated.
  (h/sql "DELETE CMR_ASSOCIATIONS_OLD WHERE ASSOCIATION_TYPE='TOOL-COLLECTION'")

  ;; rename CMR_ASSOCIATIONS_OLD table back to CMR_ASSOCIATIONS table..
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS_OLD RENAME TO CMR_ASSOCIATIONS"))
