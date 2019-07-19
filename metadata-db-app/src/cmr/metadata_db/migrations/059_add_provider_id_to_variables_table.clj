(ns cmr.metadata-db.migrations.059-add-provider-id-to-variables-table
  "Adds `provider_id` column to variables table."
  (:require
   [clojure.java.jdbc :as j]
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up from version 58 to 59."
  []
  (println "cmr.metadata-db.migrations.059-add-provider-id-to-variables-table up...")
  ;; At this point in time, UMM-Vars support is still being developed, so we
  ;; don't care about what's in the database right now, and the new field
  ;; can't be null, so:
  (h/sql "TRUNCATE TABLE METADATA_DB.cmr_variables")
  (h/sql "TRUNCATE TABLE METADATA_DB.cmr_variable_associations")
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_variables "
              "ADD provider_id VARCHAR(10) NOT NULL")))

(defn down
  "Migrates the database down from version 59 to 58."
  []
  (println "cmr.metadata-db.migrations.059-add-provider-id-to-variables-table down...")
  (h/sql "ALTER TABLE METADATA_DB.cmr_variables DROP COLUMN provider_id"))
