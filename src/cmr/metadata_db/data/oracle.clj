(ns cmr.metadata-db.data.oracle
  "Contains a record definition that implements the ConcpetStore and Lifecycle protocols
  backed by an Oracle database."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.utility :as util]))

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
  [db-config]
  (let [db (:db db-config)]
    (j/db-do-commands db "DELETE FROM METADATA_DB.concept")
    (j/db-do-commands db "DELETE FROM METADATA_DB.concept_id")))


(defrecord OracleStore
  [
   ;; A map with the configuration - no connection pooling for now
   db]
  
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
    [this concept-type provider-id native-id]
    (let [db (:db this)]
      ;; FIXME - add table locking/transaction
      ;(j/db-do-commands db "SET TRANSACTION READ WRITE")
      ;; try to get the current sequence number of this concept
      ;(j/db-do-commands db "LOCK TABLE METADATA_DB.concept_id IN EXCLUSIVE MODE")
      (let [seq-num (int (or (:sequence_number (first (j/query db ["SELECT sequence_number 
                                                                   FROM METADATA_DB.concept_id 
                                                                   WHERE concept_type = ? 
                                                                   AND provider_id = ? 
                                                                   AND native_id = ?"
                                                                   concept-type 
                                                                   provider-id 
                                                                   native-id]))) 
                             0))]
        (if (= seq-num 0)
          ;; This is a new concept so we need to save it with a new sequence number.
          ;; We check to see if the sequence has already started for this
          ;; provider/concept type and use it if so.  Otherwise we start a new
          ;; sequence at 1.
          (let [new-seq-num (inc (int (or (:msn (first (j/query db ["SELECT MAX(sequence_number)
                                                                    AS msn 
                                                                    FROM METADATA_DB.concept_id 
                                                                    WHERE concept_type = ? 
                                                                    AND provider_id = ?"
                                                                    concept-type 
                                                                    provider-id])))
                                          0)))]
            ;; Save an entry with the sequence number.
            (j/insert! db
                       "METADATA_DB.concept_id"
                       ["sequence_number"
                        "concept_type"
                        "provider_id"
                        "native_id"]
                       [new-seq-num
                        concept-type
                        provider-id
                        native-id])
            
            ;(j/db-do-commands db "COMMIT")) 
            
            (util/generate-concept-id concept-type provider-id new-seq-num))
          (util/generate-concept-id concept-type provider-id seq-num)))))
  
  
  (get-concept
    [this concept-id revision-id])
  
  (get-concepts
    [this concept-id-revision-id-tuples])
  
  (save-concept
    [this concept])
  
  (delete-concept
    [this concept-id])
  
  (force-delete
    [this]
    (reset-database this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;     

(defn create-db
  "Creates the db needed for clojure.java.jdbc library."
  []
  (println "CREATING ORACLE DB")
  (map->OracleStore {:db {:classname "oracle.jdbc.driver.OracleDriver"
                          :subprotocol "oracle"
                          :subname (format "thin:@%s:%s:%s" db-host db-port db-sid)
                          :user db-username
                          :password db-password}}))                  
