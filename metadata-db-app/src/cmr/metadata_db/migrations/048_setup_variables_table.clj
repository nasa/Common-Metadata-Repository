(ns cmr.metadata-db.migrations.048-setup-variables-table
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private variables-column-sql
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
  variable_name VARCHAR(20),
  measurement VARCHAR(1024),
  transaction_id INTEGER DEFAULT 0 NOT NULL")

(def ^:private variables-constraint-sql
  (str "CONSTRAINT variables_pk PRIMARY KEY (id), "
       ;; Unique constraint on native id and revision id
       "CONSTRAINT variables_con_rev UNIQUE (native_id, revision_id)
       USING INDEX (create unique index variables_ucr_i ON cmr_variables (native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT variables_cid_rev UNIQUE (concept_id, revision_id)
       USING INDEX (create unique index variables_cri ON cmr_variables (concept_id, revision_id))"))

(defn- create-variables-table
  []
  (h/sql
   (format "CREATE TABLE METADATA_DB.cmr_variables (%s, %s)"
           variables-column-sql variables-constraint-sql)))


(defn- create-variables-indices
  []
  (h/sql "CREATE INDEX variables_crdi ON METADATA_DB.cmr_variables (concept_id, revision_id, deleted)")
  (h/sql "CREATE INDEX variables_vn ON METADATA_DB.cmr_variables (variable_name)")
  (h/sql "CREATE INDEX variables_meas ON METADATA_DB.cmr_variables (measurement)"))

(defn- create-variables-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_variables_seq"))

(defn up
  "Migrates the database up to version 48."
  []
  (println "cmr.metadata-db.migrations.048-setup-variables-table up...")
  (create-variables-table)
  (create-variables-indices)
  (create-variables-sequence))

(defn down
  "Migrates the database down from version 48."
  []
  (println "cmr.metadata-db.migrations.048-setup-variables-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_variables_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_variables"))
