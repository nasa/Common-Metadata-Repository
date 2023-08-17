(ns cmr.aurora.connection
  "Contains functions for interacting with the Aurora DB cluster."
  (:require
   [clojure.java.jdbc :as j]
   [clojure.string :as str]
   [clj-time.coerce :as cr]
   [cmr.common.date-time-parser :as p]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [debug error info trace warn]]
   [cmr.common.util :as util]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.health-helper :as hh])
  (:import
   com.zaxxer.hikari.HikariDataSource
   software.amazon.jdbc.ds.AwsWrapperDataSource
   java.sql.DriverManager))

(defn db-spec
  [connection-pool-name db-url user password db-name]
  {:classname "org.postgresql.ds.PGSimpleDataSource"
   :subprotocol "jdbc:aws-wrapper:postgresql:"
   :dbtype "postgresql"
   :host db-url
   :port "5432"
   :dbname db-name
   :user (str/lower-case user)
   :password password
   :connection-pool-name connection-pool-name})

(defn pool
  [spec]
  (let [{:keys [classname
                subprotocol
                host
                port
                user
                password
                dbname
                connection-pool-name]} spec]
    (doto (HikariDataSource.)
      (.setMaximumPoolSize 100)
      (.setPoolName connection-pool-name)
      (.setUsername user)
      (.setPassword password)
      (.setDataSourceClassName (.getName AwsWrapperDataSource))
      (.addDataSourceProperty "jdbcProtocol" subprotocol)
      (.addDataSourceProperty "serverName" host)
      (.addDataSourceProperty "serverPort" port)
      (.addDataSourceProperty "database" dbname)
      (.addDataSourceProperty "targetDataSourceClassName" classname))))

;; (defn aurora-pool
;;   []
;;   (pool {:classname "org.postgresql.ds.PGSimpleDataSource" :subprotocol "jdbc:aws-wrapper:postgresql:" :user (aurora-config/aurora-db-user) :password (aurora-config/aurora-db-password) :connection-pool-name "AuroraPool"}))

(defrecord PostgresStore
           [;; The database spec.
            spec

            ;; The database pool of connections
            datasource]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; CMR Component Implementation
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  lifecycle/Lifecycle

  (start [this system]
    (assoc this :datasource (pool spec)))

  (stop [this system]

    #_(try
          ;; Cleanup the connection pool
        (let [pool-name (get-in this [:spec :connection-pool-name])
              hikari (HikariDataSource.)]
          (.destroyConnectionPool hikari pool-name))
        (catch Exception e
          (warn (str "Unable to destroy connection pool. It may not have started. Error: "
                     (.getMessage e)))))

    (dissoc this :datasource)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PostgresStore Constructor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db
  "Creates and returns the database connection pool."
  [spec]
  (->PostgresStore spec nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Prototype Work
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; the following db connection pool method was from the prototype, it is an 
;; alternative to the PostgresStore component/atom, not appropriate for production. 
;; How to use it:
;; (def pooled-pg-db (delay (aurora/make-prototype-pool (aurora/db-spec "connection pool name"))))
;; (defn pg-db-connection [] @pooled-pg-db)        ;; this is what you pass to jdbc lib, e.g.:
;; (jdbc/query (pg-db-connection) ["select * from some_table limit 10"])
(defn make-prototype-pool
  [spec]
  (let [cpds (pool spec)]
    {:datasource cpds}))

(defn execute-query
  [pool sql-query]
  (with-open [conn (.getConnection pool)
              stmt (.createStatement conn)
              res (.executeQuery stmt sql-query)]
    ;; return query result
    (.next res)))

(defn create-temp-table
  "Creates temporary work area used in get-concepts and force-delete-concepts-by-params functions. 
   Call from within a transaction, note that Postgres deletes temporary tables at the end of a session"
  [conn]
  (info "Creating get_concepts_work_area temporary table")
  (j/db-do-prepared conn "CREATE TEMP TABLE IF NOT EXISTS get_concepts_work_area
                  (concept_id VARCHAR(255),
                  revision_id INTEGER)
                  ON COMMIT DELETE ROWS"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PostgreSQL Timestamp Conversion Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti pg-timestamp->clj-time ;; TODO - add timezone adjusting logic if necessary
  "Converts PostgreSQL timestamp types into clj-time. Must be called within
  a with-db-transaction block with the connection"
  (fn [db pt]
    (type pt)))

(defmethod pg-timestamp->clj-time java.sql.Timestamp ;; this is the one that executes but code needs work
  [db ^java.sql.Timestamp pt]
  (let [cal (java.util.Calendar/getInstance)
        ^java.util.TimeZone gmt-tz (java.util.TimeZone/getTimeZone "GMT")]
    (.setTimeZone cal gmt-tz)
    (cr/from-sql-time (.timestampValue pt cal))))

(defmethod pg-timestamp->clj-time org.postgresql.util.PGTimestamp ;; might execute ?
  [db ^org.postgresql.util.PGTimestamp pt]
  (println "GOT TIMESTAMP TYPE: org.postgresql.util.PGTimestamp")
  (let [cal (java.util.Calendar/getInstance)
        ^java.util.TimeZone gmt-tz (java.util.TimeZone/getTimeZone "GMT")]
    (.setTimeZone cal gmt-tz)
    (cr/from-sql-time (.timestampValue pt cal))))

(defn db-timestamp->str-time ;; fall through; lacking manual timezone adjustment
  "Converts database timestamp instance into a string representation of the time. 
   Must be called within a with-db-transaction block with the connection -- TODO"
  [db ot]
  (p/clj-time->date-time-str (cr/from-sql-time ot)))