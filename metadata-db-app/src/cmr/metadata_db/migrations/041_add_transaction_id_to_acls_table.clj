(ns cmr.metadata-db.migrations.041-add-transaction-id-to-acls-table
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  []
  (h/sql
    "LOCK TABLE cmr_acls IN EXCLUSIVE MODE"
    "ALTER TABLE cmr_acls ADD transaction_id INTEGER DEFAULT 0 NOT NULL"
    "CREATE INDEX cmr_acls_crtid ON cmr_acls (concept_id, revision_id, transaction_id)"))

(defn down
  []
  (h/sql
    "LOCK TABLE cmr_acls IN EXCLUSIVE MODE"
    "ALTER TABLE cmr_acls DROP COLUMN transaction_id"
    "DROP INDEX cmr_acls_crtid"))
