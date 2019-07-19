(ns cmr.metadata-db.migrations.043-add-acl-target-provider-id-column
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  []
  (h/sql
    "ALTER TABLE cmr_acls ADD target_provider_id VARCHAR(10)"))

(defn down
  []
  (h/sql
    "ALTER TABLE cmr_acls DROP COLUMN target_provider_id"))
