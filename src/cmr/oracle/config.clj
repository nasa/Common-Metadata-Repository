(ns cmr.oracle.config
  "Contains functions for retrieving connection configuration from environment variables"
  (:require [cmr.common.config :as cfg]
            [cmr.oracle.connection :as conn]))

(def db-host (cfg/config-value-fn :db-host "localhost"))

(def db-port (cfg/config-value-fn :db-port "1521"))

(def db-sid (cfg/config-value-fn :db-sid "orcl"))

(def sys-dba-username (cfg/config-value-fn :sys-dba-username "sys as sysdba"))

(def sys-dba-password (cfg/config-value-fn :sys-dba-password "oracle"))

(defn sys-dba-db-spec
  []
  (conn/db-spec
    (db-host)
    (db-port)
    (db-sid)
    (sys-dba-username)
    (sys-dba-password)))