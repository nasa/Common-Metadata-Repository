(ns cmr.oracle.config
  "Contains functions for retrieving connection configuration from environment variables"
  (:require [cmr.common.config :as cfg]
            [cmr.oracle.connection :as conn]))

(def db-url (cfg/config-value-fn :db-url "thin:@localhost:1521:orcl"))

(def db-fcf-enabled
  "Enables or disables fast connection failover in Oracle jdbc."
  (cfg/config-value-fn :db-fcf-enabled "false" #(Boolean. ^String %)))

(def db-ons-config (cfg/config-value-fn :db-ons-config ""))

(def sys-dba-username (cfg/config-value-fn :sys-dba-username "sys as sysdba"))

(def sys-dba-password (cfg/config-value-fn :sys-dba-password "oracle"))

(defn sys-dba-db-spec
  []
  (conn/db-spec
    "sys-dba-connection-pool"
    (db-url)
    (db-fcf-enabled)
    (db-ons-config)
    (sys-dba-username)
    (sys-dba-password)))