(ns cmr.metadata-db.migrations.056-setup-services-table
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private services-column-sql
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
  service_name VARCHAR(80),
  transaction_id INTEGER DEFAULT 0 NOT NULL")

(def ^:private services-constraint-sql
  (str "CONSTRAINT services_pk PRIMARY KEY (id), "
       ;; Unique constraint on native id and revision id
       "CONSTRAINT services_con_rev UNIQUE (native_id, revision_id)
       USING INDEX (create unique index services_ucr_i ON cmr_services (native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT services_cid_rev UNIQUE (concept_id, revision_id)
       USING INDEX (create unique index services_cri ON cmr_services (concept_id, revision_id))"))

(defn- create-services-table
  []
  (h/sql
   (format "CREATE TABLE METADATA_DB.cmr_services (%s, %s)"
           services-column-sql services-constraint-sql)))


(defn- create-services-indices
  []
  ;; Supports queries to find service revisions that are deleted
  (h/sql "CREATE INDEX services_crdi ON METADATA_DB.cmr_services (concept_id, revision_id, deleted)")
  ;; Supports queries to find services by service name
  (h/sql "CREATE INDEX services_vn ON METADATA_DB.cmr_services (service_name)"))

(defn- create-services-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_services_seq"))

(defn up
  "Migrates the database up to version 56."
  []
  (println "cmr.metadata-db.migrations.056-setup-services-table up...")
  (create-services-table)
  (create-services-indices)
  (create-services-sequence))

(defn down
  "Migrates the database down from version 56."
  []
  (println "cmr.metadata-db.migrations.056-setup-services-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_services_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_services"))
