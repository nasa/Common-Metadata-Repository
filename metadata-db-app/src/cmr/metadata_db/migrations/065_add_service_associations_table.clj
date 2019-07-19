(ns cmr.metadata-db.migrations.065-add-service-associations-table
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private service-assocs-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1500) NOT NULL,
  service_concept_id VARCHAR(255) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  associated_concept_id VARCHAR(255) NOT NULL,
  associated_revision_id INTEGER NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  user_id VARCHAR(30),
  transaction_id NUMBER DEFAULT 0 NOT NULL")

(def ^:private service-assocs-constraint-sql
  (str "CONSTRAINT s_assoc_pk PRIMARY KEY (id), "
        ;; Unique constraint on native id and revision id
        "CONSTRAINT s_assoc_con_rev UNIQUE (native_id, revision_id)
         USING INDEX (create unique index s_assoc_ucr_i ON CMR_SERVICE_ASSOCIATIONS (native_id, revision_id)), "

        ;; Unique constraint on concept id and revision id
        "CONSTRAINT s_assoc_cid_rev UNIQUE (concept_id, revision_id)
         USING INDEX (create unique index s_assoc_cri ON CMR_SERVICE_ASSOCIATIONS (concept_id, revision_id))"))

(defn- create-service-associations-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.CMR_SERVICE_ASSOCIATIONS (%s, %s)"
                 service-assocs-column-sql service-assocs-constraint-sql)))

(defn- create-service-associations-indices
  []
  (h/sql "CREATE INDEX s_assoc_crdi ON CMR_SERVICE_ASSOCIATIONS (concept_id, revision_id, deleted)")
  (h/sql "CREATE INDEX s_assoc_acari ON CMR_SERVICE_ASSOCIATIONS (associated_concept_id, associated_revision_id)"))

(defn- create-service-associations-sequence
  []
  (h/sql "CREATE SEQUENCE CMR_SERVICE_ASSOCIATIONS_SEQ"))

(defn up
  "Migrates the database up to version 65."
  []
  (println "cmr.metadata-db.migrations.065-add-service-associations-table up...")
  (create-service-associations-table)
  (create-service-associations-indices)
  (create-service-associations-sequence))

(defn down
  "Migrates the database down from version 65."
  []
  (println "cmr.metadata-db.migrations.065-add-service-associations-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.CMR_SERVICE_ASSOCIATIONS_SEQ")
  (h/sql "DROP TABLE METADATA_DB.CMR_SERVICE_ASSOCIATIONS"))
