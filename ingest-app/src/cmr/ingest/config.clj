(ns cmr.ingest.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]
            [cmr.message-queue.config :as rmq-conf]))

(defconfig ingest-accept-umm-version
  "Defines the latest umm version accepted by ingest - it's the latest official version.  
   This environment variable needs to be manually set when newer UMM version becomes official"
  {:default "1.9"}) 

(defconfig ingest-username
  "Ingest database username"
  {:default "CMR_INGEST"})

(defconfig ingest-password
  "Ingest database password"
  {})

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

(defconfig provider-exchange-name
  "The ingest exchange to which provider change messages are published."
  {:default "cmr_ingest_provider.exchange"})

(defn queue-config
  "Returns the rabbit mq configuration for the ingest application."
  []
  (assoc (rmq-conf/default-config)
         :exchanges [(provider-exchange-name)]))

(defconfig ingest-nrepl-port
  "Port to listen for nREPL connections."
  {:default nil :parser cfg/maybe-long})

(defconfig return-umm-json-validation-errors
  "Flag for whether or not UMM-JSON validation errors should be returned for collections."
  {:default false :type Boolean})

(defconfig return-umm-spec-validation-errors
  "Flag for whether or not UMM Spec validation errors should be returned for collections."
  {:default true :type Boolean})
