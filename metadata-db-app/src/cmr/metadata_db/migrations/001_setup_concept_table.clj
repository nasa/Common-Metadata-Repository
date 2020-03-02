(ns cmr.metadata-db.migrations.001-setup-concept-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 1."
  []
  (println "cmr.metadata-db.migrations.001-setup-concept-table up...")
  (h/sql "CREATE TABLE METADATA_DB.concept (
                              concept_type VARCHAR(255) NOT NULL,
                              native_id VARCHAR(255) NOT NULL,
                              concept_id VARCHAR(255) NOT NULL,
                              provider_id VARCHAR(255) NOT NULL,
                              metadata VARCHAR(4000) NOT NULL,
                              format VARCHAR(255) NOT NULL,
                              revision_id INTEGER DEFAULT 0 NOT NULL,
                              deleted INTEGER DEFAULT 0 NOT NULL,
                              CONSTRAINT unique_concept_revision
                              UNIQUE (concept_type, provider_id, native_id, revision_id)
                              USING INDEX (create unique index ucr_index on concept(concept_type, provider_id, native_id, revision_id)),
                              CONSTRAINT unique_concept_id_revision
                              UNIQUE (concept_id, revision_id)
                              USING INDEX (create unique index cid_rev_indx on concept(concept_id, revision_id)))")
  (h/sql "CREATE INDEX concept_id_indx ON METADATA_DB.concept(concept_id)"))

(defn down
  "Migrates the database down from version 1."
  []
  (println "cmr.metadata-db.migrations.001-setup-concept-table down...")
  (try
    (h/sql "DROP TABLE METADATA_DB.concept")
    (catch Exception e)))
