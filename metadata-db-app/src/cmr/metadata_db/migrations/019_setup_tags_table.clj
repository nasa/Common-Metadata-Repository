(ns cmr.metadata-db.migrations.019-setup-tags-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(def ^:private tags-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1030) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  user_id VARCHAR(30)")

(def ^:private tags-constraint-sql
  (str "CONSTRAINT tags_pk PRIMARY KEY (id), "
       ;; Unique constraint on native id and revision id
       "CONSTRAINT tags_con_rev UNIQUE (native_id, revision_id)
       USING INDEX (create unique index tags_ucr_i ON tags (native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT tags_cid_rev UNIQUE (concept_id, revision_id)
       USING INDEX (create unique index tags_cri ON tags (concept_id, revision_id))"))

(defn- create-tags-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.tags (%s, %s)" tags-column-sql tags-constraint-sql)))


(defn- create-tags-indices
  []
  (h/sql "CREATE INDEX tags_crdi ON tags (concept_id, revision_id, deleted)"))

(defn- create-tags-sequence
  []
  (h/sql "CREATE SEQUENCE tags_seq"))

(defn up
  "Migrates the database up to version 19."
  []
  (println "cmr.metadata-db.migrations.019-setup-tags-table up...")
  (create-tags-table)
  (create-tags-indices)
  (create-tags-sequence))

(defn down
  "Migrates the database down from version 19."
  []
  (println "cmr.metadata-db.migrations.019-setup-tags-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.tags_seq")
  (h/sql "DROP TABLE METADATA_DB.tags"))
