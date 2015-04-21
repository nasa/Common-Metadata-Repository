(ns cmr.oracle.connection
  "Contains a record definition that implements the ConcpetStore and Lifecycle protocols
  backed by an Oracle database."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [clj-time.coerce :as cr]
            [clj-time.core :as t]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh])
  (:import oracle.ucp.jdbc.PoolDataSourceFactory
           oracle.ucp.admin.UniversalConnectionPoolManagerImpl))


(defn db-spec
  [connection-pool-name db-url fcf-enabled ons-config user password]
  {:classname "oracle.jdbc.pool.OracleDataSource"
   :subprotocol "oracle"
   :subname db-url
   :user user
   :password password
   :connection-pool-name connection-pool-name
   :fcf-enabled fcf-enabled
   :ons-config ons-config})

(defn- db-conn-info-safe-for-logging
  "Returns the database connection info without password details"
  [oracle-store]
  (pr-str (-> oracle-store
              :spec
              (assoc :password "*****"))))

(defn health-fn
  "Returns the health status of the database by executing some sql."
  [oracle-store]
  (try
    (if (= [{:a 1M}]
           (j/query oracle-store "select 1 a from dual"))
      {:ok? true}
      {:ok? false :problem "Could not select data from database."})
    (catch Exception e
      (info "Database conn info" (db-conn-info-safe-for-logging oracle-store))
      {:ok? false :problem (.getMessage e)})))

(defn health
  "Returns the oracle health with timeout handling."
  [oracle-store]
  (hh/get-health #(health-fn oracle-store) 30000))

(defn db->oracle-conn
  "Gets an oracle connection from the outer database connection. Should be called from within as
  with-db-transaction block."
  [db]
  (if-let [proxy-conn (:connection db)]
    proxy-conn
    (errors/internal-error!
      (str "Called db->oracle-conn with connection that was not within a db transaction. "
           "It must be called from within call j/with-db-transaction"))))

(defmulti oracle-timestamp->clj-time
  "Converts oracle.sql.TIMESTAMP and related instances into a clj-time. Must be called within
  a with-db-transaction block with the connection"
  (fn [db ot]
    (type ot)))

(defmethod oracle-timestamp->clj-time oracle.sql.TIMESTAMPTZ
  [db ^oracle.sql.TIMESTAMPTZ ot]
  (let [^java.sql.Connection conn (db->oracle-conn db)]
    (cr/from-sql-time (.timestampValue ot conn))))

(defmethod oracle-timestamp->clj-time oracle.sql.TIMESTAMP
  [db ^oracle.sql.TIMESTAMP ot]
  (let [cal (java.util.Calendar/getInstance)]
    (.setTimeZone cal (java.util.TimeZone/getTimeZone "GMT"))
    (cr/from-sql-time (.timestampValue ot cal))))

(defn current-db-time
  "Retrieves the current time from the database as a clj-time instance."
  [oracle-store]
  (j/with-db-transaction
    [conn oracle-store]
    (->> (j/query conn "select systimestamp from dual")
         first
         :systimestamp
         (oracle-timestamp->clj-time conn))))

(defn pool
  [spec]
  (let [{:keys [classname
                subprotocol
                subname
                user
                password
                connection-pool-name
                fcf-enabled
                ons-config]} spec]
    (doto (PoolDataSourceFactory/getPoolDataSource)
      (.setConnectionFactoryClassName classname)
      (.setConnectionPoolName connection-pool-name)
      (.setUser user)
      (.setPassword password)
      (.setURL (str "jdbc:" subprotocol ":" subname))
      (.setMinPoolSize 5)
      (.setMaxPoolSize 100)
      (.setInactiveConnectionTimeout 60)
      (.setConnectionWaitTimeout 600)
      (.setPropertyCycle 120)
      (.setFastConnectionFailoverEnabled fcf-enabled)
      (.setONSConfiguration ons-config)
      (.setAbandonedConnectionTimeout 3500)
      (.setValidateConnectionOnBorrow true)
      (.setSQLForValidateConnection "select 1 from DUAL"))))

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
         (assoc this :datasource (pool spec)))

  (stop [this system]

        (try
          ;; Cleanup the connection pool
          (let [pool-name (get-in this [:spec :connection-pool-name])
                ucp-manager (UniversalConnectionPoolManagerImpl/getUniversalConnectionPoolManager)]
            (.destroyConnectionPool ucp-manager pool-name))
          (catch Exception e
            (warn (str "Unable to destroy connection pool. It may not have started. Error: "
                       (.getMessage e)))))

        (dissoc this :datasource)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db
  "Creates and returns the database connection pool."
  [spec]
  (->OracleStore spec nil))