(ns cmr.metadata-db.migrations.062-add-variable-constraint
  (:require
   [config.mdb-migrate-helper :as h]))

(defn- drop-old-constraint
  []
  (h/sql
    "ALTER TABLE METADATA_DB.cmr_variables DROP CONSTRAINT variables_con_rev"))

(defn- add-variable-constraint
  []
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_variables "
              "ADD CONSTRAINT variables_con_n_r_p "
              "UNIQUE (native_id, revision_id, provider_id) "
              "USING INDEX (CREATE UNIQUE INDEX variables_idx_n_r_p "
              "ON cmr_variables (native_id, revision_id, provider_id))")))

(defn- drop-new-constraint
  []
  (h/sql
    "ALTER TABLE METADATA_DB.cmr_variables DROP CONSTRAINT variables_con_n_r_p"))

(defn- re-add-old-variable-constraint
  []
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_variables "
              "ADD CONSTRAINT variables_con_rev "
              "UNIQUE (native_id, revision_id) "
              "USING INDEX (CREATE UNIQUE INDEX variables_ucr_i "
              "ON cmr_variables (native_id, revision_id))")))

(defn up
  "Migrates the database up to version 62."
  []
  (println "cmr.metadata-db.migrations.062-add-variable-constraint up...")
  (drop-old-constraint)
  (add-variable-constraint))

(defn down
  "Migrates the database down from version 62."
  []
  (println "cmr.metadata-db.migrations.062-add-variable-constraint down...")
  (drop-new-constraint)
  (re-add-old-variable-constraint))
