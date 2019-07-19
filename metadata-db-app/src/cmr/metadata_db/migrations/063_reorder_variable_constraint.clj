(ns cmr.metadata-db.migrations.063-reorder-variable-constraint
  (:require
   [config.mdb-migrate-helper :as h]))

(defn- drop-constraint
  []
  (h/sql
    "ALTER TABLE METADATA_DB.cmr_variables DROP CONSTRAINT variables_con_n_r_p"))

(defn- add-new-constraint
  []
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_variables "
              "ADD CONSTRAINT variables_con_n_r_p "
              "UNIQUE (provider_id, native_id, revision_id) "
              "USING INDEX (CREATE UNIQUE INDEX variables_idx_n_r_p "
              "ON cmr_variables (provider_id, native_id, revision_id))")))

(defn- re-add-old-constraint
  []
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_variables "
              "ADD CONSTRAINT variables_con_n_r_p "
              "UNIQUE (native_id, revision_id, provider_id) "
              "USING INDEX (CREATE UNIQUE INDEX variables_idx_n_r_p "
              "ON cmr_variables (native_id, revision_id, provider_id))")))

(defn up
  "Migrates the database up to version 63."
  []
  (println "cmr.metadata-db.migrations.063-reorder-variable-constraint up...")
  (drop-constraint)
  (add-new-constraint))

(defn down
  "Migrates the database down from version 63."
  []
  (println "cmr.metadata-db.migrations.063-reorder-variable-constraint down...")
  (drop-constraint)
  (re-add-old-constraint))
