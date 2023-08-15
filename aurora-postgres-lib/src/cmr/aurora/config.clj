(ns cmr.aurora.config
  "Contains functions for retrieving Aurora Postgres connection configuration from environment variables"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.aurora.connection :as conn]))

(defconfig aurora-cluster
  "Aurora cluster name"
  {:default "cmr-aurora-cluster"})

(defconfig aurora-db-name
  "Aurora database name"
  {:default "cmrcdb"})

(defconfig db-url-primary
  "Primary db endpoint (for writes and reads)"
  {:default "localhost"})

(defconfig db-url-secondary
  "Secondary db url (for reads only)
   Note that for local development this is the same as db-url-primary since there is no local Aurora cluster mock-up."
  {:default "localhost"})

(defn db-connection-str
  "Returns the connection string for the given postgres db endpoint"
  [host]
  (str "jdbc:aws-wrapper:postgresql://" host ":5432/" aurora-db-name))

(defconfig aurora-db-user
  "Aurora database master user"
  {:default "postgres"})

(defconfig aurora-db-password
  "Aurora database master password"
  {:default "admin"})

(defconfig aurora-toggle
  "Three-way toggle for Aurora functionality. 'aurora-off' uses only Oracle, 'aurora-on' uses both Oracle and Aurora, and 'aurora-only' uses only Aurora"
  {:default "aurora-off"})

(defn sys-dba-db-spec
  []
  (conn/db-spec
    "pg-sys-dba-connection-pool"
    (db-url-primary)
    (aurora-db-user)
    (aurora-db-password)
    (aurora-db-name)))