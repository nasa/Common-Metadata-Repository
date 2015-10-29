(ns cmr.bootstrap.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]))

(defconfig bootstrap-username
  "Defines the bootstrap database username."
  {***REMOVED***})

(defconfig bootstrap-password
  "Defines the bootstrap database password."
  {***REMOVED***})

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

(defconfig db-synchronization-enabled
  "Defines whether db-synchronization is enabled."
  {:default true
   :type Boolean})

(defconfig virtual-product-provider-aliases
  "For each provider-id for which a virtual product is configured, define a set of provider-ids
  which have the same virtual product configuration as the original."
  {:default {"LPDAAC_ECS"  #{}
             "GSFCS4PA" #{}}
   :type :edn})
