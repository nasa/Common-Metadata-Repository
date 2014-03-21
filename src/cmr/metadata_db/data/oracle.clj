(ns cmr.metadata-db.data.oracle
  "Contains a record definition that implements the ConcpetStore and Lifecycle protocols
  backed by an Oracle database."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as ]))

;;; Constants

(def concept-id-prefix-length 1)

(def db-username (get (System/getenv) "MDB_DB_USERNAME" "METADATA_DB"))
(def db-password (get (System/getenv) "MDB_DB_PASSWORD" "METADATA_DB"))
(def db-host (get (System/getenv) "MDB_DB_HOST" "localhost"))
(def db-port (get (System/getenv) "MDB_DB_PORT" "1521"))
(def db-sid (get (System/getenv) "MDB_DB_SID" "orcl"))

;;; Utility methods

(defn reset-database
  "Delete everything from the concpet table."
  [])

(defrecord OracleStore
  [
   ;; An atom containing a map with the configuration - no connection pooling for now
   db-config]
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle
  
  (start [this system]
         (reset-database this)
         this)
  
  (stop [this system]
        this)
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data/ConceptStore
  
  (get-concept-id
    [this concept-type provider-id native-id])
  
  (get-concept
    [this concept-id revision-id])
  
  (get-concepts
    [this concept-id-revision-id-tuples])
  
  (save-concept
    [this concept]
    (validate-concept concept))
  
  (delete-concept
    [this concept-id])
  
  (force-delete
    [this]
    (reset-database this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;     

(defn create-db
  "Creates the db needed for clojure.java.jdbc library."
  []
  (map->OracleStore {:classname "oracle.jdbc.driver.OracleDriver"
                     :subprotocol "oracle"
                     :subname (format "thin:@%s:%s:%s" db-host db-port db-sid)
                     :user db-username
                     :password db-password}))                  
