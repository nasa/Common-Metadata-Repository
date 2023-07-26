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
   com.zaxxer.hikari.HikariDataSource
   software.amazon.jdbc.ds.AwsWrapperDataSource
   java.sql.DriverManager))

(defn pool
  [spec]
  (let [{:keys [classname
                subprotocol
                user
                password
                connection-pool-name]} spec]
    (doto (HikariDataSource.)
      (.setMaximumPoolSize 100)
      (.setPoolName connection-pool-name)
      (.setUsername user)
      (.setPassword password)
      (.setDataSourceClassName (.getName AwsWrapperDataSource))
      (.addDataSourceProperty "jdbcProtocol" subprotocol)
      (.addDataSourceProperty "serverName" (aurora-config/db-url-primary))
      (.addDataSourceProperty "serverPort" "5432")
      (.addDataSourceProperty "database" (aurora-config/aurora-db-name))
      (.addDataSourceProperty "targetDataSourceClassName" classname))))

(defn aurora-pool
  []
  (pool {:classname "org.postgresql.ds.PGSimpleDataSource" :subprotocol "jdbc:aws-wrapper:postgresql:" :user (aurora-config/aurora-db-user) :password (aurora-config/aurora-db-password) :connection-pool-name "AuroraPool"}))

(defn execute-query
  [sql-query]
  (with-open [conn (.getConnection aurora-pool)
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
