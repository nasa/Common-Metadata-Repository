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
  synchronously send http requests to the indexer to index concepts. The queue_with_fallback_to_http
  configuration means that the message queue will be used, but if there is an error queueing a
  message, ingest will send an http request to the indexer to index the concept.

  Valid values are \"queue\", \"http\", and \"queue_with_fallback_to_http\""
  {:default "http"})

(defn use-index-queue?
  "Returns true if ingest is configured to use the message queue for indexing and false otherwise."
  []
  (case (indexing-communication-method)
    ("queue" "queue_with_fallback_to_http") true
    false))

(defn queue-fallback-to-http?
  "Returns true if ingest is configured to fallback to sending an http request to the indexer to
  ingest a request if it fails to queue a message on the message queue. Otherwise returns false."
  []
  (= "queue_with_fallback_to_http" (indexing-communication-method)))

(defconfig publish-queue-timeout-ms
  "Number of milliseconds to wait for a publish request to be confirmed before considering the
  request timed out."
  {:default 60000 :type Long})
