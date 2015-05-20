(ns cmr.ingest.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]))

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

(defconfig index-queue-name
  "Queue used for requesting indexing of concepts"
  {:default "cmr_index.queue"})

(defconfig indexing-communication-method
  "Used to determine whether the ingest will queue messages asynchronously for the indexer, or will
  synchronously send http requests to the indexer to index concepts. Valid values are \"queue\"
  and \"http\"."
  {:default "http"})

(defn use-index-queue?
  "Returns true if ingest is configured to use the message queue for indexing and false otherwise."
  []
  (= "queue" (indexing-communication-method)))

(defconfig publish-queue-timeout-ms
  "Number of milliseconds to wait for a publish request to be confirmed before considering the
  request timed out."
  {:default 10000 :type Long})

(defconfig ingest-nrepl-port
  "Port to listen for nREPL connections."
  {:default nil :parser cfg/maybe-long})
