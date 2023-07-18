(ns cmr.aurora.connection
  "Contains functions for interacting with the Aurora DB cluster."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clj-time.coerce :as cr]
   [cmr.common.log :refer [debug error info trace warn]]
   [cmr.common.util :as util]
   [cmr.aurora.config :as aurora-config]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.health-helper :as hh])
  (:import
   java.sql.DriverManager
   java.util.Properties))

(defn db-properties
  [username password]
  (doto (Properties.)
    ;; Configuring connection properties for the underlying JDBC driver.
    (.setProperty "user" username)
    (.setProperty "password" password)
    (.setProperty "loginTimeout" "100")
    ;; Configuring connection properties for the Aurora JDBC Wrapper.
    (.setProperty "wrapperPlugins" "failover,efm")
    (.setProperty "wrapperLogUnclosedConnections" "true")))

(defn execute-query
  [sql-query]
  (with-open [conn (DriverManager/getConnection connection-string (db-properties
                                                                   aurora-config/aurora-db-user
                                                                   aurora-config/aurora-db-password))
              stmt (.createStatement conn)
              res (.executeQuery stmt sql-query)]
    ;; return query result
    (.next res)))

(defn save-concept
  "Saves a concept to Aurora Postgres"
  [concept]
  (info "Made a call to aurora.connection/save-concept"))

(defn get-concept
  "Gets a concept from Aurora Postgres"
  ([provider concept-type concept-id]
   (info "Made a call to aurora.connection/get-concept"))
  ([provider concept-type concept-id revision-id]
   (info "Made a call to aurora.connection/get-concept with revision-id")))

(defn get-concepts
  "Gets a group of concepts from Aurora Postgres"
  [provider concept-type concept-id-revision-id-tuples]
  (info "Made a call to aurora.connection/get-concepts"))

(defn get-concepts-small-table
  "Gets a group of concepts from Aurora Postgres using provider-id, concept-id, revision-id tuples"
  [concept-type provider-concept-revision-tuples]
  (info "Made a call to aurora.connection/get-concepts-small-table"))

(defn delete-concept
  "Deletes a concept from Aurora Postgres"
  [provider concept-type concept-id revision-id]
  (info "Made a call to aurora.connection/delete-concept"))

(defn delete-concepts
  "Deletes multiple concepts from Aurora Postgres"
  [provider concept-type concept-id-revision-id-tuples]
  (info "Made a call to aurora.connection/delete-concepts"))
