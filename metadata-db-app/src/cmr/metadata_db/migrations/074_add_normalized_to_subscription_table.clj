(ns cmr.metadata-db.migrations.074-add-normalized-to-subscription-table
  (:require
   [cheshire.core :as json]
   [cmr.common.util :as util]
   [cmr.ingest.services.subscriptions-helper :as jobs]
   [config.mdb-migrate-helper :as helper]))

(defn result->query
  "From a Database result, extract out the query and convert it to a usable string.
   The algorithum on how to normalize the query may change over time, for this
   migration version 1 will be used."
  [result]
  (-> (:metadata result)
        util/gzip-blob->string
        (as-> % (json/parse-string %, true))
        :Query
        jobs/normalize-parameters-v1))

(defn populate-new-column
  "Pull out the requested query from the matadata, then normalize the data and
   populate the new 'normalized_query' column."
  []
  (doseq [result (helper/query "SELECT id, concept_id, metadata FROM cmr_subscriptions")]
    (let [id (:id result)
          query (result->query result)
          sql-statment (format "UPDATE cmr_subscriptions SET normalized_query='%s' WHERE id=%s" query id)]
      (helper/sql sql-statment))))

(defn up
    "Migrates the database up to version 74."
    []
    (println "cmr.metadata-db.migrations.074-update-subscription-table up...")

    ;; The Normalized_Query column holds a version of the Subscription Query which
    ;; has been normalized to a standard followed by both EDSC and CMR. Both
    ;; applications can assume the normalized query is the same despite changes
    ;; in query order or other such nominal differences. Simple string compair
    ;; can be used on the normalized query.
    (helper/sql "alter table cmr_subscriptions add normalized_query VARCHAR2(4000)")
    (populate-new-column))

(defn down
  "Migrates the database down from version 74."
  []
  (println "cmr.metadata-db.migrations.074-update-subscription-table down.")
  (helper/sql "alter table cmr_subscriptions drop column normalized_query"))
