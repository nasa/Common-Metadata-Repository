(ns cmr.metadata-db.migrations.082-migrate-tag-associations
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private tag-assocs-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1500) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  tag_key varchar(1030) NOT NULL,
  associated_concept_id VARCHAR(255) NOT NULL,
  associated_revision_id INTEGER NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  user_id VARCHAR(30),
  transaction_id NUMBER DEFAULT 0 NOT NULL")

(def ^:private tag-assocs-constraint-sql
  (str "CONSTRAINT tag_assocs_pk PRIMARY KEY (id), "
       ;; Unique constraint on native id and revision id
       "CONSTRAINT tag_assocs_con_rev UNIQUE (native_id, revision_id)
        USING INDEX (create unique index tag_assocs_ucr_i ON cmr_tag_associations (native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT tag_assocs_cid_rev UNIQUE (concept_id, revision_id)
        USING INDEX (create unique index tag_assocs_cri ON cmr_tag_associations (concept_id, revision_id))"))

(defn- create-tag-associations-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_tag_associations (%s, %s)"
                 tag-assocs-column-sql tag-assocs-constraint-sql)))


(defn- create-tag-associations-indices
  []
  (h/sql "CREATE INDEX tag_assoc_crdi ON cmr_tag_associations (concept_id, revision_id, deleted)")
  (h/sql "CREATE INDEX tag_assoc_acari ON cmr_tag_associations (associated_concept_id, associated_revision_id)")
  (h/sql "CREATE INDEX tag_assoc_tkri ON cmr_tag_associations (tag_key, revision_id)")
  (h/sql "CREATE INDEX tag_assoc_tkcid ON cmr_tag_associations (tag_key, associated_concept_id)")
  (h/sql "CREATE INDEX tag_assoc_tkcrid ON cmr_tag_associations (tag_key, associated_concept_id, associated_revision_id)"))

(defn- create-tag-associations-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_tag_associations_seq"))


(defn up
  "Migrates the database up to version 82."
  []
  (println "cmr.metadata-db.migrations.082-migrate-tag-associations up...")
 
  ;; rename CMR_TAG_ASSOCIATIONS table to prevent new entries being inserted.
  (h/sql "ALTER TABLE CMR_TAG_ASSOCIATIONS RENAME TO CMR_TAG_ASSOCIATIONS_OLD")

  ;; Migrate tag association data to CMR_ASSOCIATIONS table.
  ;; Note: We will need to use the CMR_ASSOCIATIONS_SEQ.nextval for the ID column to make it unique in the new table.
  (h/sql "INSERT INTO CMR_ASSOCIATIONS (ID,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,SOURCE_CONCEPT_IDENTIFIER,ASSOCIATION_TYPE)
          SELECT CMR_ASSOCIATIONS_SEQ.nextval,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,TAG_KEY,'TAG-COLLECTION'
          FROM CMR_TAG_ASSOCIATIONS_OLD")

  ;; Drop the old table.
  (h/sql "DROP TABLE CMR_TAG_ASSOCIATIONS_OLD")

  ;; Drop the sequence
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_tag_associations_seq")

  ;; The following index exists in CMR_TAG_ASSOCIATIONS but not in CMR_ASSOCIATIONS, needs to be added.
  (h/sql "CREATE INDEX tag_assoc_tkcid ON CMR_ASSOCIATIONS (SOURCE_CONCEPT_IDENTIFIER, ASSOCIATED_CONCEPT_ID)"))
  
(defn down
  "Migrates the database down from version 82."
  []
  (println "cmr.metadata-db.migrations.082-migrate-tag-associations down...")

  ;; rename CMR_ASSOCIATIONS table to prevent new entries being inserted.
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS RENAME TO CMR_ASSOCIATIONS_OLD")

  ;; Drop index tag_assoc_tkcid from CM_ASSOCIATIONS table so that it can be
  ;; re-created in CMR_TAG_ASSOCIATIONS.
  (h/sql "DROP INDEX tag_assoc_tkcid")
 
  ;; Create CMR_TAG_ASSOCIATIONS table.
  (create-tag-associations-table)
  (create-tag-associations-indices)
  (create-tag-associations-sequence)

  ;; Migrate tag associations back to CMR_TAG_ASSOCIATIONS table.
  ;; Note: We will now need to use the CMR_TAG_ASSOCIATIONS_SEQ.nextval for the ID column so that
  ;; when new entries are inserted, all the ids will be unique.
  (h/sql "INSERT INTO CMR_TAG_ASSOCIATIONS (ID,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,TAG_KEY)
          SELECT CMR_TAG_ASSOCIATIONS_SEQ.nextval,CONCEPT_ID,NATIVE_ID,METADATA,FORMAT,REVISION_ID,ASSOCIATED_CONCEPT_ID,ASSOCIATED_REVISION_ID,REVISION_DATE,DELETED,USER_ID,TRANSACTION_ID,SOURCE_CONCEPT_IDENTIFIER
          FROM CMR_ASSOCIATIONS_OLD
          WHERE ASSOCIATION_TYPE='TAG-COLLECTION'")

  ;; Remove all the tag associations migrated.
  (h/sql "DELETE CMR_ASSOCIATIONS_OLD WHERE ASSOCIATION_TYPE='TAG-COLLECTION'")

  ;; rename CMR_ASSOCIATIONS_OLD table back to CMR_ASSOCIATIONS table..
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS_OLD RENAME TO CMR_ASSOCIATIONS"))
