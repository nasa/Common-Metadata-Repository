(ns cmr.metadata-db.migrations.078-add-subscription-type-to-subscription-table
  (:require
   [cheshire.core :as json]
   [cmr.common.util :as util]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [config.mdb-migrate-helper :as helper]))

(defn populate-new-column
  "Updates new subscription_type column with default granule value"
  []
  (helper/sql (format "UPDATE cmr_subscriptions SET subscription_type='granule'")))

(defn- recreate-subscription-index
  []
  (helper/sql "DROP INDEX cmr_subs_ccisinq")
  (helper/sql "CREATE INDEX cmr_subs_ccisinq ON CMR_SUBSCRIPTIONS
              (collection_concept_id, subscriber_id, normalized_query, subscription_type)"))

(defn up
  "Migrates the database up to version 78."
  []
  (println "cmr.metadata-db.migrations.078-update-subscription-table up...")
  (helper/sql "alter table cmr_subscriptions add subscription_type VARCHAR2(64)")
  (populate-new-column)
  (recreate-subscription-index))

(defn down
  "Migrates the database down from version 78."
  []
  (println "cmr.metadata-db.migrations.078-update-subscription-table down."))
