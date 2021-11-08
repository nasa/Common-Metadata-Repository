(ns cmr.metadata-db.migrations.076-increase-variable-name-length
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 76."
  []
  (println "cmr.metadata-db.migrations.076-increase-variable-name-length up...")
  ;; Variable Name has been at 256 since UMM-Var v1.7, but column has not been
  ;; updated to match. Latest verion is currently v1.8, so we are increasing
  ;; column length to match the schema
  (h/sql "alter table METADATA_DB.cmr_variables modify variable_name VARCHAR(256)"))


(defn down
  "Migrates the database down from version 76."
  []
  (println "cmr.metadata-db.migrations.076-increase-variable-name-length down...")
  ;; Roll back to column to length of 80, but first truncate any values to match the length
  (h/sql "update METADATA_DB.cmr_variables set variable_name = substr(variable_name,1,80)")
  (h/sql "alter table METADATA_DB.cmr_variables modify variable_name VARCHAR(80)"))
