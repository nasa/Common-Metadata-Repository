(ns cmr.metadata-db.migrations.021-setup-provider-services-tables
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.concept-tables :as ct]))

(defn up
  "Migrates the database up to version 21."
  []
  (println "cmr.metadata-db.migrations.021-setup-provider-services-tables up...")
  (doseq [provider (h/get-regular-providers)]
    (println (str "Provider " (:provider-id provider)))
    (ct/create-concept-table (config/db) provider :service)
    (ct/create-concept-table-id-sequence (config/db) provider :service)))

(defn down
  "Migrates the database down from version 21."
  []
  (println "cmr.metadata-db.migrations.021-setup-provider-services-tables down...")
  (doseq [provider (h/get-regular-providers)
          :let [t (ct/get-table-name provider :service)
                sequence-name (str t "_seq")]]
    (println (str "Table " t))
    (h/sql (format "drop table %s" t))
    (h/sql (format "drop sequence %s" sequence-name))))