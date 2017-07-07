(ns migrations.058-update-variable-name-length
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 58."
  []
  (println "migrations.058-update-variable-name-length up...")
  (h/sql "alter table METADATA_DB.cmr_variables modify variable_name VARCHAR(80) NOT NULL"))


(defn down
  "Migrates the database down from version 58."
  []
  (println "migrations.058-update-variable-name-length down...")
  ;; We don't want to roll back the length change
  (h/sql "alter table METADATA_DB.cmr_variables modify variable_name VARCHAR(80) NULL"))
