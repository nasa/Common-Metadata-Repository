(ns cmr.metadata-db.migrations.032-setup-tag-associations-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(def ^:private tag-assocs-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1500) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  associated_concept_id VARCHAR(255) NOT NULL,
  associated_revision_id INTEGER NOT NULL,
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
  (h/sql "CREATE INDEX tag_assoc_acari ON cmr_tag_associations (associated_concept_id, associated_revision_id)"))

(defn- create-tag-associations-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_tag_associations_seq"))

(defn up
  "Migrates the database up to version 32."
  []
  (println "cmr.metadata-db.migrations.032-setup-tag-associations-table up...")
  (create-tag-associations-table)
  (create-tag-associations-indices)
  (create-tag-associations-sequence))

(defn down
  "Migrates the database down from version 32."
  []
  (println "cmr.metadata-db.migrations.032-setup-tag-associations-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_tag_associations_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_tag_associations"))
