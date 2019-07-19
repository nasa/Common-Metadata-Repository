(ns cmr.metadata-db.migrations.060-update-var-association-var-name-length
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 60."
  []
  (println "cmr.metadata-db.migrations.060-update-var-association-var-name-length up...")
  ;; The variable_name field actually saves the native-id of the variable in the association
  ;; It is temporary. We didn't change the name of the field to native_id as we will eventaully
  ;; change this to concept_id later as described in CMR-4254
  (h/sql "alter table METADATA_DB.cmr_variable_associations modify variable_name VARCHAR(1030)"))


(defn down
  "Migrates the database down from version 60."
  []
  (println "cmr.metadata-db.migrations.060-update-var-association-var-name-length down...")
  (h/sql "update METADATA_DB.cmr_variable_associations set variable_name = SUBSTR(variable_name, 0, 80)")
  (h/sql "alter table METADATA_DB.cmr_variable_associations modify variable_name VARCHAR(80)"))
