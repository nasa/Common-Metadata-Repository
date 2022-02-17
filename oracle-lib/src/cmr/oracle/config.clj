(ns cmr.oracle.config
  "Contains functions for retrieving connection configuration from environment variables"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.connection :as conn]))

(defconfig db-url
  "db url"
  {:default "thin:@localhost:1521/orcl"})

(defconfig db-fcf-enabled
  "Enables or disables fast connection failover in Oracle jdbc."
  {:default false
   :type Boolean})

(defconfig db-ons-config
  "db-ons-config"
  {:default ""})

(defconfig sys-dba-username
  "system dba username"
  {:default "sys as sysdba"})

(defconfig sys-dba-password
  "system dba password"
  {})

(defn sys-dba-db-spec
  []
  (conn/db-spec
    "sys-dba-connection-pool"
    (db-url)
    (db-fcf-enabled)
    (db-ons-config)
    (sys-dba-username)
    (sys-dba-password)))
