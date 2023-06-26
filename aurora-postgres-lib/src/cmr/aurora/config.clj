(ns cmr.aurora.config
  "Contains functions for retrieving Aurora Postgres connection configuration from environment variables"
  (:require [cmr.common.config :as cfg :refer [defconfig]]))

(defconfig aurora-cluster
  "Aurora cluster name"
  {:default "cmr-aurora-cluster"})

(defconfig aurora-db-name
  "Aurora database name"
  {:default "metadata_db"})

(defconfig db-url-primary
  "Primary db url (for writes and reads)"
  {:default (str "jdbc:aws-wrapper:postgresql://localhost:5432/" aurora-db-name)})

(defconfig db-url-secondary
  "Secondary db url (for reads only)
   Note that for local development this is the same as db-url-primary since there is no local Aurora cluster mock-up."
  {:default (str "jdbc:aws-wrapper:postgresql://localhost:5432/" aurora-db-name)})

(defconfig aurora-db-user
  "Aurora database user"
  {:default "admin"})

(defconfig aurora-db-password
  "Aurora database password"
  {:default "admin"})

(defconfig aurora-toggle
  "Three-way toggle for Aurora functionality. 'aurora-off' uses only Oracle, 'aurora-on' uses both Oracle and Aurora, and 'aurora-only' uses only Aurora"
  {:default "aurora-off"})