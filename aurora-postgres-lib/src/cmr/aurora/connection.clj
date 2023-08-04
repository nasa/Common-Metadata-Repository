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
   [cmr.aurora.config :as aurora-config]
   [cmr.aurora.sql-utils :as su :refer [insert values select from where with order-by desc delete as]]
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

(defn sys-dba-db-spec
  []
  (db-spec
   "pg-sys-dba-connection-pool"
   (aurora-config/db-url-primary)
   (aurora-config/aurora-db-user)
   (aurora-config/aurora-db-password)
   (aurora-config/aurora-db-name)))

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

(def pooled-db (delay {:datasource (pool sys-dba-db-spec)}))

(defn db-connection [] @pooled-db)

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

;; NOTE that the below CRUD functions are not fit for general use, rather
;; they are specific to a very rapid prototype effort that makes use of
;; pre-existing metadata-db-app code, dependencies, and JDBC transaction flow,
;; thus as-is will only work in that highly specific context.
;; Some of the functions below only build a SQL statement, 
;; others execute SQL but need to be provided the connection,
;; because they need to execute in the same JDBC transaction as other code,
;; and some receive the SQL generated for Oracle because it is the same and too entangled to separate.

(defn save-concept
  "Saves a concept to Aurora Postgres"
  [table cols seq-name ]
  (info "Made a call to aurora.connection/save-concept")
  (format (str "INSERT INTO %s (id, %s, transaction_id) VALUES "
               "(NEXTVAL('%s'),%s,NEXTVAL('GLOBAL_TRANSACTION_ID_SEQ'))")
          table
          (str/join "," cols)
          seq-name
          (str/join "," (repeat (count values) "?"))))

(defn get-concept
  "Gets a concept from Aurora Postgres"
  ([conn table concept-id]
   (info "Made a call to aurora.connection/get-concept")
   (su/find-one conn (select '[*]
                             (from table)
                             (where `(= :concept-id ~concept-id))
                             (order-by (desc :revision-id)))))
  ([conn table concept-id revision-id]
   (info "Made a call to aurora.connection/get-concept with revision-id")
   (su/find-one conn (select '[*]
                             (from table)
                             (where `(and (= :concept-id ~concept-id)
                                          (= :revision-id ~revision-id)))))))

(defn get-concepts
  "Gets a group of concepts from Aurora Postgres"
  [conn stmt]
  (info "Made a call to aurora.connection/get-concepts")
  (su/query conn stmt))

(defn gen-get-concepts-sql-with-temp-table
  "To generate the SQL statement for getting a group of concepts using the temp table method"
  [table]
  (su/build (select [:c.*]
                    (from (as (keyword table) :c)
                          (as :get-concepts-work-area :t))
                    (where `(and (= :c.concept-id :t.concept-id)
                                 (= :c.revision-id :t.revision-id))))))

(defn get-concepts-small-table
  "Gets a group of concepts from Aurora Postgres using provider-id, concept-id, revision-id tuples"
  [conn stmt]
  (info "Made a call to aurora.connection/get-concepts-small-table")
  (su/query conn stmt))

(defn delete-concept
  "Deletes a concept from Aurora Postgres"
  [table concept-id revision-id]
  (info "Made a call to aurora.connection/delete-concept")
  (su/build (delete table
                    (where `(and (= :concept-id ~concept-id)
                                 (= :revision-id ~revision-id))))))

(defn delete-concepts
  "Deletes multiple concepts from Aurora Postgres"
  [provider concept-type concept-id-revision-id-tuples]
  (info "Made a call to aurora.connection/delete-concepts"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PostgreSQL Timestamp Conversion Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti pg-timestamp->clj-time
  "Converts PostgreSQL timestamp types into clj-time. Must be called within
  a with-db-transaction block with the connection"
  (fn [db pt]
    (type pt)))

(defmethod pg-timestamp->clj-time java.sql.Timestamp
  [db ^java.sql.Timestamp pt]
  (println "GOT TIMESTAMP TYPE: java.sql.Timestamp")
  (let [cal (java.util.Calendar/getInstance)
        ^java.util.TimeZone gmt-tz (java.util.TimeZone/getTimeZone "GMT")]
    (.setTimeZone cal gmt-tz)
    (cr/from-sql-time (.timestampValue pt cal))))

(defmethod pg-timestamp->clj-time org.postgresql.util.PGTimestamp
  [db ^org.postgresql.util.PGTimestamp pt]
  (println "GOT TIMESTAMP TYPE: org.postgresql.util.PGTimestamp")
  (let [cal (java.util.Calendar/getInstance)
        ^java.util.TimeZone gmt-tz (java.util.TimeZone/getTimeZone "GMT")]
    (.setTimeZone cal gmt-tz)
    (cr/from-sql-time (.timestampValue pt cal))))

(defn db-timestamp->str-time
  "Converts database timestamp instance into a string representation of the time. 
   Must be called within a with-db-transaction block with the connection"
  [db ot]
  (p/clj-time->date-time-str (pg-timestamp->clj-time db ot)))