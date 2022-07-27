(ns cmr.metadata-db.migrations.081-rename-variable-associations
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 81."
  []
  (println "cmr.metadata-db.migrations.081-rename-variable-associations up...")
  (h/sql "ALTER TABLE CMR_VARIABLE_ASSOCIATIONS RENAME COLUMN VARIABLE_CONCEPT_ID TO SOURCE_CONCEPT_IDENTIFIER")
  (h/sql "ALTER TABLE CMR_VARIABLE_ASSOCIATIONS ADD ASSOCIATION_TYPE VARCHAR(30) DEFAULT 'VARIABLE-COLLECTION' NOT NULL")
  (h/sql "CREATE INDEX assoc_ati ON CMR_VARIABLE_ASSOCIATIONS (ASSOCIATION_TYPE)")
  (h/sql "ALTER TABLE CMR_VARIABLE_ASSOCIATIONS RENAME TO CMR_ASSOCIATIONS")
  (h/sql "RENAME CMR_VARIABLE_ASSOCIATIONS_SEQ TO CMR_ASSOCIATIONS_SEQ"))

(defn down
  "Migrates the database down from version 81."
  []
  (println "cmr.metadata-db.migrations.081-rename-variable-associations down...")
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS RENAME COLUMN SOURCE_CONCEPT_IDENTIFIER TO VARIABLE_CONCEPT_ID")
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS DROP COLUMN ASSOCIATION_TYPE")
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS RENAME TO CMR_VARIABLE_ASSOCIATIONS")
  (h/sql "RENAME CMR_ASSOCIATIONS_SEQ TO CMR_VARIABLE_ASSOCIATIONS_SEQ"))
