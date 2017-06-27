(ns migrations.058-add-provider-id-to-variables-table
  "Adds `provider_id` column to variables table."
  (:require
   [clojure.java.jdbc :as j]
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up from version 57 to 58."
  []
  (println "migrations.058-add-provider-id-to-variables-table up...")
  ;; At this point in time, UMM-Vars support is still being developed, so we
  ;; don't care about what's in the database right now, and the new field
  ;; can't be null, so:
  (h/sql "TRUNCATE TABLE METADATA_DB.cmr_variables")
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_variables "
              "ADD provider_id VARCHAR(255) NOT NULL "
              "ADD CONSTRAINT variables_p_uniq UNIQUE (provider_id)"))
  (h/sql "CREATE INDEX variables_p_vn ON METADATA_DB.cmr_variables (provider_id, variable_name)"))

(defn down
  "Migrates the database down from version 58 to 57."
  []
  (println "migrations.058-add-provider-id-to-variables-table down...")
  (h/sql "ALTER TABLE METADATA_DB.cmr_variables DROP COLUMN provider_id"))
