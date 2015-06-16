(ns migrations.016-add-small-to-provider-table
  "Adds small field to provider table"
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.concept-tables :as ct]))

(defn up
  "Migrates the database up to version 16."
  []
  (println "migrations.016-add-small-to-provider-table up...")
  (h/sql "alter table providers add small INTEGER DEFAULT 0 NOT NULL")
  ;; Create the SMALL_PROV tables and sequence
  ;; We use a dummy provider (the key is :small true) to invoke the create tables code.
  (let [db (config/db)]
    (ct/create-provider-concept-tables db {:provider-id "IGNORED"
                                           :cmr-only true
                                           :small true})))

(defn down
  "Migrates the database down from version 16."
  []
  (println "migrations.016-add-small-to-provider-table down...")
  ;; Drop the SMALL_PROV tables and sequence
  (let [db (config/db)]
    (ct/delete-provider-concept-tables db {:provider-id "IGNORED"
                                           :cmr-only true
                                           :small true}))
  (h/sql "alter table providers drop column small"))

