(ns cmr.metadata-db.migrations.030-create-transaction-id-index
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.oracle.sql-utils :as utils]))

(defn up
  "Migrates the database up to version 30."
  []
  (println "cmr.metadata-db.migrations.030-create-transaction-id-index up...")
  (doseq [table (h/get-concept-tablenames :collection :granule :service :tag :access-group)]
    (utils/ignore-already-exists-errors "INDEX"
      (h/sql
       (format "LOCK TABLE %s IN EXCLUSIVE MODE" table)
       (format "CREATE INDEX %s_crtid ON %s (concept_id, revision_id, transaction_id)" table table)))))
(defn down
  "Migrates the database down from version 30."
  []
  (println "030-create-transaction-id-index down...")
  (doseq [table (h/get-concept-tablenames :collection :granule :service :tag :access-group)]
    (h/sql
     (format "LOCK TABLE %s IN EXCLUSIVE MODE" table)
     (format "DROP INDEX %s_crtid" table))))
