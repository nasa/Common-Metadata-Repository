(ns cmr.metadata-db.migrations.061-change-variable-associations-schema
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 61."
  []
  (println "cmr.metadata-db.migrations.061-change-variable-associations-schema up...")
  ;; At this point, there is no operational variable associations
  ;; So we just wipe out the existing variable associations and start over
  (h/sql "TRUNCATE TABLE METADATA_DB.cmr_variable_associations")
  (h/sql "ALTER TABLE METADATA_DB.cmr_variable_associations DROP COLUMN variable_name")
  (h/sql "ALTER TABLE METADATA_DB.cmr_variable_associations ADD variable_concept_id VARCHAR(255) NOT NULL"))

(defn down
  "Migrates the database down from version 61."
  []
  (println "cmr.metadata-db.migrations.061-change-variable-associations-schema down...")
  (h/sql "ALTER TABLE METADATA_DB.cmr_variable_associations DROP COLUMN variable_concept_id")
  (h/sql "ALTER TABLE METADATA_DB.cmr_variable_associations ADD variable_name VARCHAR(1030) NOT NULL"))
