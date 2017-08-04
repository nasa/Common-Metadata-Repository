(ns cmr.bootstrap.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.message-queue.config :as queue-config]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.connection :as conn]))

(defconfig bootstrap-username
  "Defines the bootstrap database username."
  {:default "CMR_BOOTSTRAP"})

(defconfig bootstrap-password
  "Defines the bootstrap database password."
  {})

(defn db-spec
  "Returns a db spec populated with config information that can be used to connect to oracle"
  [connection-pool-name]
  (conn/db-spec
    connection-pool-name
    (oracle-config/db-url)
    (oracle-config/db-fcf-enabled)
    (oracle-config/db-ons-config)
    (bootstrap-username)
    (bootstrap-password)))

(defconfig bootstrap-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message queue configuration

(defconfig bootstrap-provider-exchange-name
   "The bootstrap exchange to which provider bootstrap messages are published."
   {:default "cmr-bootstrap-provider-exchange"})

(defconfig bootstrap-provider-queue-name
  "The queue containing bootstrap provider events."
  {:default "cmr-bootstrap-provider-queue"})

(defconfig bootstrap-provider-queue-listener-count
  "Number of worker threads to use for the queue listener for the bootstrap provider queue"
  {:default 1
   :type Long})

(defn queue-config
  "Returns the queue configuration for the bootstrap application."
  []
  (assoc (queue-config/default-config)
         :queues [(bootstrap-provider-queue-name)]
         :exchanges [(bootstrap-provider-exchange-name)]
         :queues-to-policies {(bootstrap-provider-queue-name) {:max-tries 1
                                                               ;; 6 hours visibility timeout
                                                               :visibility-timeout-secs 21600}}
         :queues-to-exchanges
         {(bootstrap-provider-queue-name) [(bootstrap-provider-exchange-name)]}))
