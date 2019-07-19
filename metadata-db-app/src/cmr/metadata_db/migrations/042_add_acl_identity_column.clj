(ns cmr.metadata-db.migrations.042-add-acl-identity-column
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  []
  (h/sql
    "ALTER TABLE cmr_acls ADD acl_identity VARCHAR(1030) DEFAULT '' NOT NULL"))

(defn down
  []
  (h/sql
    "ALTER TABLE cmr_acls DROP COLUMN acl_identity"))
