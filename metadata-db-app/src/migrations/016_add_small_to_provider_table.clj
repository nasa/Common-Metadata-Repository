(ns migrations.016-add-small-to-provider-table
  "Adds small field to provider table"
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.providers]
            [cmr.metadata-db.data.providers :as p]))

(defn up
  "Migrates the database up to version 16."
  []
  (println "migrations.016-add-small-to-provider-table up...")
  (h/sql "alter table providers add small INTEGER DEFAULT 0 NOT NULL")
  ;; Create the SMALL_PROV provider
  (let [db (config/db)]
    (when-not (p/get-provider db p/small-provider-id)
      (p/save-provider db {:provider-id p/small-provider-id
                           :cmr-only true
                           :small true}))))

(defn down
  "Migrates the database down from version 16."
  []
  (println "migrations.016-add-small-to-provider-table down...")
  ;; Drop the SMALL_PROV provider
  (let [db (config/db)]
    (when (p/get-provider db p/small-provider-id)
      ;; set the small provider's small flag to false, so that it can be deleted
      (h/sql "update providers set small = 0 where provider_id = 'SMALL_PROV'")
      (p/delete-provider db p/small-provider-id)))
  (h/sql "alter table providers drop column small"))

