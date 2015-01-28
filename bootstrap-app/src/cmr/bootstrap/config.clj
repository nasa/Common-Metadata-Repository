(ns cmr.bootstrap.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]))

(def db-username
  (cfg/config-value-fn :bootstrap-username "CMR_BOOTSTRAP"))

(def db-password
  (cfg/config-value-fn :bootstrap-password "CMR_BOOTSTRAP"))

(defn db-spec
  "Returns a db spec populated with config information that can be used to connect to oracle"
  [connection-pool-name]
  (conn/db-spec
    connection-pool-name
    (oracle-config/db-url)
    (oracle-config/db-fcf-enabled)
    (oracle-config/db-ons-config)
    (db-username)
    (db-password)))

(def parallel-chunk-size
  "Gets the number of concepts that should be processed in each thread of get-concepts."
  (cfg/config-value-fn :parallel-n "200" #(Integer/parseInt ^String %)))

(def result-set-fetch-size
  "Gets the setting for query fetch-size (number of rows to fetch at once)"
  (cfg/config-value-fn :result-set-fetch-size "200" #(Integer/parseInt ^String %)))
