(ns cmr.bootstrap.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.message-queue.config :as queue-config]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.connection :as conn]
   [cmr.aurora.config :as aurora-config]
   [cmr.aurora.connection :as pg-conn]))

(defconfig bootstrap-username
  "Defines the bootstrap database username."
  {:default "CMR_BOOTSTRAP"})

(defconfig bootstrap-password
  "Defines the bootstrap database password."
  {})

(defn db-spec
  "Returns a db spec populated with config information that can be used to connect to postgres"
  [connection-pool-name]
  (pg-conn/db-spec
   connection-pool-name
   (aurora-config/db-url-primary)
   (bootstrap-username)
   (bootstrap-password)
   (aurora-config/aurora-db-name)))

(defconfig bootstrap-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message queue configuration

(defconfig bootstrap-exchange-name
   "The bootstrap exchange to which bootstrap messages are published."
   {:default "cmr-bootstrap-exchange"})

(defconfig bootstrap-queue-name
  "The queue containing bootstrap events."
  {:default "cmr-bootstrap-queue"})

(defconfig bootstrap-queue-listener-count
  "Number of worker threads to use for the queue listener for the bootstrap queue."
  {:default 1
   :type Long})

(defn queue-config
  "Returns the queue configuration for the bootstrap application."
  []
  (assoc (queue-config/default-config)
         :queues [(bootstrap-queue-name)]
         :exchanges [(bootstrap-exchange-name)]
         :queues-to-policies {(bootstrap-queue-name) {:max-tries 1
                                                      ;; max 12 hours visibility timeout
                                                      :visibility-timeout-secs 43200}}
         :queues-to-exchanges {(bootstrap-queue-name)
                               [(bootstrap-exchange-name)]}))
