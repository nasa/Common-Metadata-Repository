(ns cmr.metadata-db.migrations.079-update-subscription-constraint
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 79."
  []
  (println "cmr.metadata-db.migrations.079-update-subscription-constraint up...")
  (h/sql "ALTER TABLE cmr_subscriptions DROP CONSTRAINT subscriptions_con_npr")
  (h/sql "ALTER TABLE cmr_subscriptions ADD CONSTRAINT subscriptions_nid_rid UNIQUE (native_id, revision_id)
  USING INDEX (create unique index subscriptions_unri ON cmr_subscriptions (native_id, revision_id))"))

(defn down
  "Migrates the database down from version 79."
  []
  (println "cmr.metadata-db.migrations.079-update-subscription-constraint down...")
  (h/sql "ALTER TABLE cmr_subscriptions DROP CONSTRAINT subscriptions_nid_rid")
  (h/sql "ALTER TABLE cmr_subscriptions ADD CONSTRAINT subscriptions_con_npr UNIQUE (native_id, revision_id, provider_id)
  USING INDEX (create unique index subscriptions_idx_npr ON cmr_subscriptions (native_id, revision_id, provider_id))"))
