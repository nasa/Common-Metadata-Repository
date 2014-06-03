(ns cmr.oracle.config
  "Contains functions for retrieving connection configuration from environment variables"
  (:require [cmr.common.config :as cfg]))

(def db-username (cfg/config-value-fn :db-username "METADATA_DB"))

(def db-password (cfg/config-value-fn :db-password "METADATA_DB"))

(def db-host (cfg/config-value-fn :db-host "localhost"))

(def db-port (cfg/config-value-fn :db-port "1521"))

(def db-sid (cfg/config-value-fn :db-sid "orcl"))

(defn db-spec-args
  "Returns a vector of arguments for instantiating the db-spec."
  []
  [(db-host)
   (db-port)
   (db-sid)
   (db-username)
   (db-password)])