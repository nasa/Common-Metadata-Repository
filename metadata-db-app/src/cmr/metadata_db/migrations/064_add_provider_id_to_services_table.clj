(ns cmr.metadata-db.migrations.064-add-provider-id-to-services-table
  "Adds `provider_id` column to services table."
  (:require
   [clojure.java.jdbc :as j]
   [config.mdb-migrate-helper :as h]))

(defn- drop-old-constraint
  []
  (h/sql
   "ALTER TABLE METADATA_DB.cmr_services DROP CONSTRAINT services_con_rev"))

(defn- add-new-constraint
  []
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_services "
              "ADD CONSTRAINT services_con_p_n_r "
              "UNIQUE (native_id, revision_id, provider_id) "
              "USING INDEX (CREATE UNIQUE INDEX services_idx_p_n_r "
              "ON cmr_services (provider_id, native_id, revision_id))")))

(defn- drop-new-constraint
  []
  (h/sql
   "ALTER TABLE METADATA_DB.cmr_services DROP CONSTRAINT services_con_p_n_r"))

(defn- re-add-old-constraint
  []
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_services "
              "ADD CONSTRAINT services_con_rev "
              "UNIQUE (native_id, revision_id) "
              "USING INDEX (CREATE UNIQUE INDEX services_ucr_i "
              "ON cmr_services (native_id, revision_id))")))

(defn up
  "Migrates the database up from version 63 to 64."
  []
  (println "cmr.metadata-db.migrations.064-add-provider-id-to-services-table up...")
  ;; At this point in time, UMM-Services support is still being developed, so we
  ;; don't care about what's in the database right now, and the new field can't
  ;; be null, so:
  (h/sql "TRUNCATE TABLE METADATA_DB.cmr_services")
  (h/sql (str "ALTER TABLE METADATA_DB.cmr_services "
              "ADD provider_id VARCHAR(10) NOT NULL"))
  (drop-old-constraint)
  (add-new-constraint))

(defn down
  "Migrates the database down from version 64 to 63."
  []
  (println "cmr.metadata-db.migrations.064-add-provider-id-to-services-table down...")
  (drop-new-constraint)
  (re-add-old-constraint)
  (h/sql "ALTER TABLE METADATA_DB.cmr_services DROP COLUMN provider_id"))
