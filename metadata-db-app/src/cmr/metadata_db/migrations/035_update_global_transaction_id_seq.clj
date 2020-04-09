(ns cmr.metadata-db.migrations.035-update-global-transaction-id-seq
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  []
  (println "cmr.metadata-db.migrations.035-update-global-transaction-id-seq up...")
  (h/sql "ALTER SEQUENCE metadata_db.global_transaction_id_seq ORDER")
  (h/sql "ALTER SEQUENCE metadata_db.migration_transaction_id_seq ORDER"))

(defn down
  []
  (println "cmr.metadata-db.migrations.035-update-global-transaction-id-seq down...")
  (h/sql "ALTER SEQUENCE metadata_db.global_transaction_id_seq NOORDER")
  (h/sql "ALTER SEQUENCE metadata_db.migration_transaction_id_seq NOORDER"))
