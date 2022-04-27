(ns cmr.metadata-db.migrations.078-add-subscription-type-to-subscription-table
  (:require
   [cheshire.core :as json]
   [cmr.common.util :as util]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [config.mdb-migrate-helper :as helper]))

(defn result->type
  "From a Database result, extract out the query and convert it to a usable string."
  [result]
  (let [metadata (-> (:metadata result)
                     util/gzip-blob->string
                     (as-> % (json/parse-string %, true)))
        collection-concept-id (:CollectionConceptId metadata)
        subscription-type (:Type metadata)]
    (or subscription-type
        (when collection-concept-id
          "granule")
        "collection")))

(defn populate-new-column
  "Pull out the Type from the matadata, then populate the new 'subscription_type' column."
  []
  (doseq [result (helper/query "SELECT id, concept_id, metadata FROM cmr_subscriptions")]
    (let [id (:id result)
          subscription-type (result->type result)
          sql-statment (format "UPDATE cmr_subscriptions SET subscription_type='%s' WHERE id=%s" subscription-type id)]
      (helper/sql sql-statment))))

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
