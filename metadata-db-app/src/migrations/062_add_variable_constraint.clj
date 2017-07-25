(ns migrations.062-add-variable-constraint
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private table-name "cmr_variables")
(def ^:private table (format "METADATA_DB.%s" table-name))
(def ^:private new-constraint-name "variables_con_n_r_p")
(def ^:private new-index-name "variables_idx_n_r_p")
(def ^:private old-constraint-name "variables_con_rev")
(def ^:private old-index-name "variables_ucr_i")

(defn- add-variable-constraint
  []
  (h/sql
   (format (str "ALTER TABLE %s "
                "ADD CONSTRAINT %s "
                "UNIQUE (native_id, revision_id, provider_id) "
                "USING INDEX (CREATE UNIQUE INDEX %s ON %s "
                "(native_id, revision_id, provider_id))")
           table new-constraint-name new-index-name table-name)))

(defn- drop-constraint
  [constraint-name]
  (h/sql
    (format "ALTER TABLE %s DROP CONSTRAINT %s" table constraint-name)))

(defn- drop-index
  [index-name]
  (h/sql
    (format "DROP INDEX %s" index-name)))

(defn- re-add-old-variable-constraint
  []
  (h/sql
   (format (str "ALTER TABLE %s "
                "ADD CONSTRAINT %s "
                "UNIQUE (native_id, revision_id) "
                "USING INDEX (CREATE UNIQUE INDEX %s ON %s "
                "(native_id, revision_id))")
           table old-constraint-name old-index-name table-name)))

(defn up
  "Migrates the database up to version 61."
  []
  (println "migrations.062-add-variable-constraint up...")
  (drop-constraint old-constraint-name)
  (add-variable-constraint))

(defn down
  "Migrates the database down from version 61."
  []
  (println "migrations.062-add-variable-constraint down...")
  (drop-constraint new-constraint-name)
  (re-add-old-variable-constraint))
