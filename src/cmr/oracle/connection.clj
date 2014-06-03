(ns cmr.oracle.connection
 "Contains a record definition that implements the ConcpetStore and Lifecycle protocols
  backed by an Oracle database."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource))


(defn db-spec
  [db-host db-port db-sid user password]
  {:classname "oracle.jdbc.driver.OracleDriver"
   :subprotocol "oracle"
   :subname (format "thin:@%s:%s:%s" db-host db-port db-sid)
   :user user
   :password password})


(defn test-db-connection!
  "Tests the database connection. Throws an exception if unable to execute some sql."
  [oracle-store]
  (when-not (= [{:a 1M}]
               (j/query oracle-store "select 1 a from dual"))
    (throw (Exception. "Could not select data from database."))))

(defn pool
  [spec]
  (doto (ComboPooledDataSource.)
    (.setDriverClass (:classname spec))
    (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
    (.setUser (:user spec))
    (.setPassword (:password spec))
    ;; expire excess connections after 30 minutes of inactivity:
    (.setMaxIdleTimeExcessConnections (* 30 60))
    ;; expire connections after 3 hours of inactivity:
    (.setMaxIdleTime (* 3 60 60))))

(defrecord OracleStore
  [
   ;; The database spec.
   spec

   ;; The database pool of connections
   datasource
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start [this system]
         (let [this (assoc this :datasource (pool spec))]
           (test-db-connection! this)
           this))

  (stop [this system]
        this))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db
  "Creates and returns the database connection pool."
  [spec]
  (->OracleStore spec nil))
