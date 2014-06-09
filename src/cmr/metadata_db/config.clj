(ns cmr.metadata-db.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]))

(def app-port (cfg/config-value-fn :metadata-db-port 3001 #(Long. %)))

(def db-username
  (cfg/config-value-fn :metadata-db-username "METADATA_DB"))

(def db-password
  (cfg/config-value-fn :metadata-db-password "METADATA_DB"))

(def catalog-rest-db-username
  (cfg/config-value-fn :catalog-rest-db-username "DEV_52_CATALOG_REST"))

(defn db-spec
  "Returns a db spec populated with config information that can be used to connect to oracle"
  []
  (conn/db-spec
    (oracle-config/db-url)
    (db-username)
    (db-password)))
