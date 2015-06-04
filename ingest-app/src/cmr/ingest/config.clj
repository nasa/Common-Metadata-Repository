(ns cmr.ingest.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]
            [cmr.message-queue.config :as rmq-conf]))

(defconfig ingest-username
  "Ingest database username"
  {***REMOVED***})

(defconfig ingest-password
  "Ingest database password"
  {***REMOVED***})

(defn db-spec
  "Returns a db spec populated with config information that can be used to connect to oracle"
  [connection-pool-name]
  (conn/db-spec
    connection-pool-name
    (oracle-config/db-url)
    (oracle-config/db-fcf-enabled)
    (oracle-config/db-ons-config)
    (ingest-username)
    (ingest-password)))

(defconfig ingest-exchange-name
  "The ingest exchange to which messages are published."
  {:default "cmr_ingest.exchange"})

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the ingest application."
  []
  (assoc (rmq-conf/default-config)
         :exchanges [(ingest-exchange-name)]))

(defconfig publish-queue-timeout-ms
  "Number of milliseconds to wait for a publish request to be confirmed before considering the
  request timed out."
  {:default 10000 :type Long})

(defconfig ingest-nrepl-port
  "Port to listen for nREPL connections."
  {:default nil :parser cfg/maybe-long})
