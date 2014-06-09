(ns cmr.oracle.config
  "Contains functions for retrieving connection configuration from environment variables"
  (:require [cmr.common.config :as cfg]
            [cmr.oracle.connection :as conn]))

(def db-url (cfg/config-value-fn :db-url "thin:@localhost:1521:orcl"))

(def sys-dba-username (cfg/config-value-fn :sys-dba-username "sys as sysdba"))

(def sys-dba-password (cfg/config-value-fn :sys-dba-password "oracle"))

(defn sys-dba-db-spec
  []
  (conn/db-spec
    (db-url)
    (sys-dba-username)
    (sys-dba-password)))