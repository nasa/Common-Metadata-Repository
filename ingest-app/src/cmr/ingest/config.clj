(ns cmr.ingest.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]))

(def db-username
  (cfg/config-value-fn :ingest-username "CMR_INGEST"))

(def db-password
  (cfg/config-value-fn :ingest-password "CMR_INGEST"))

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

(def index-queue-name
  "Queue used for requesting indexing of concepts"
  (cfg/config-value-fn :index-queue-name "cmr_index.queue"))

(def indexing-communication-method
  "Either \"http\" or \"queue\""
  (cfg/config-value-fn :indexing-communication-method "http"))

(def use-index-queue?
  "Boolean flag indicating whether or not to use the message queue for indexing"
  #(= "queue" (indexing-communication-method)))

(defconfig publish-queue-timeout-ms
  "Number of milliseconds to wait for a publish request to be confirmed before considering the
  request timed out."
  {:default 60000 :type Long})