(ns cmr.metadata-db.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]
            [cmr.message-queue.config :as rmq-conf]))

(defconfig metadata-db-port
  "Port metadata-db application listens on."
  {:default 3001 :type Long})

(def db-username
  (cfg/config-value-fn :metadata-db-username "METADATA_DB"))

(def db-password
  (cfg/config-value-fn :metadata-db-password "METADATA_DB"))

(def catalog-rest-db-username
  (cfg/config-value-fn :catalog-rest-db-username "DEV_52_CATALOG_REST"))

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

(defconfig metadata-db-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig deleted-collection-revision-exchange-name
  "An exchange that will have messages passed to it whenever a collection revision is removed
  from metadata db."
  {:default "cmr_deleted_collection_revision.exchange"})

(defconfig publish-collection-revision-deletes
  "This indicates whether or not collection revision deletes will be published to the exchange"
  {:default true :type Boolean})

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the metadata db application."
  []
  (assoc (rmq-conf/default-config)
         :exchanges [(deleted-collection-revision-exchange-name)]))

(defconfig publish-timeout-ms
  "Number of milliseconds to wait for a publish request to be confirmed before considering the
  request timed out."
  {:default 10000 :type Long})


