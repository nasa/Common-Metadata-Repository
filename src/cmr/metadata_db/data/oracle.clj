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

(defn clob-to-string
  "Turn an Oracle Clob into a String"
  [clob]
  (with-open [rdr (java.io.BufferedReader. (.getCharacterStream clob))]
    (apply str (line-seq rdr))))

(defn fix-metadata-field
  "Convert the metadata field from a CLOB to a string."
  [concept]
  (assoc concept :metadata (clob-to-string (:metadata concept))))

(defn reset-database
  "Delete everything from the concept table and reset the sequence."
  [db-config]
  (let [db (:db db-config)]
    (j/db-do-commands db "DELETE FROM METADATA_DB.concept")
    (try (j/db-do-commands db "DROP SEQUENCE METADATA_DB.concept_id_seq")
      (catch Exception e)) ; don't care if the sequence was not there
    (j/db-do-commands db "CREATE SEQUENCE METADATA_DB.concept_id_seq
                         MINVALUE 1
                         START WITH 1
                         INCREMENT BY 1
                         CACHE 20")))

(defn- db-result->concept-map
  "Translate concept result returned from db into a concept map"
  [result]
  (if result
    (let [{:keys [concept_type, native_id, concept_id, provider_id, metadata, format, revision_id deleted]} result]
      {:concept-type concept_type
       :native-id native_id
       :concept-id concept_id
       :provider-id provider_id
       :metadata metadata
       :format format
       :revision-id (int revision_id)
       :deleted (not= (int deleted) 0)})))

(defn- get-concept-from-db
  [db concept-id revision-id]
  (if revision-id
    (db-result->concept-map (first (j/query db ["SELECT concept_type, native_id, concept_id, provider_id, metadata, format, revision_id, deleted
                                                FROM METADATA_DB.concept
                                                WHERE concept_id = ? AND revision_id = ?"
                                                concept-id
                                                revision-id])))
    (db-result->concept-map (first (j/query db ["SELECT concept_type, native_id, concept_id, provider_id, metadata, format, revision_id, deleted
                                                FROM METADATA_DB.concept
                                                WHERE concept_id = ?
                                                ORDER BY revision_id DESC" concept-id])))))

(defn- save-in-db
  "Saves the concept in database and returns the revision-id"
  [db concept-type native-id concept-id provider-id metadata format revision-id deleted]
  (j/insert! db
             "METADATA_DB.concept"
             ["concept_type"
              "native_id"
              "concept_id"
              "provider_id"
              "metadata"
              "format"
              "revision_id"
              "deleted"]
             [concept-type
              native-id
              concept-id
              provider-id
              metadata
              format
              revision-id
              deleted])
  revision-id)

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
    (let [db (:db this)
          concept-id (first (j/query db ["SELECT concept_id
                                         FROM METADATA_DB.concept
                                         WHERE concept_type = ?
                                         AND provider_id = ?
                                         AND native_id = ?"
                                         concept-type
                                         provider-id
                                         native-id]))]
      (if concept-id
            (:concept_id concept-id)
            ;; TODO - move all error mesage templates into a common library
            (errors/throw-service-error :not-found "Concept with concept-type %s
                                                   provider-id %s
                                                   native-id %s
                                                   does not exist."
                                                   concept-type
                                                   provider-id
                                                   native-id))))
  
  
  
  
  ;   (util/generate-concept-id concept-type provider-id new-seq-num))
  ; (util/generate-concept-id concept-type provider-id seq-num)))))
  
  (get-concept
    [this concept-id revision-id]
    (if-let [concept (get-concept-from-db (:db this) concept-id revision-id)]
      concept
      (if revision-id
        (errors/throw-service-error :not-found
                                    "Could not find concept with concept-id of %s and revision %s."
                                    concept-id
                                    revision-id)
        (errors/throw-service-error :not-found
                                    "Could not find concept with concept-id of %s."
                                    concept-id))))
  
  (get-concepts
    [this concept-id-revision-id-tuples]
    (pprint concept-id-revision-id-tuples)
    ;; use a temporary table to insert our values so we can use a join to 
    ;; pull evertying in one select
    (try 
      (j/db-do-commands db "SET TRANSACTION READ WRITE")
      (j/db-do-commands db "LOCK TABLE METADATA_DB.get_concepts_work_area IN EXCLUSIVE MODE")
      (let [insert-args (conj (conj concept-id-revision-id-tuples :transaction) false)]
        
        (apply j/insert! db
               "METADATA_DB.get_concepts_work_area"
               ["concept_id" 
                "revision_id"]
               insert-args))
      (let [db-concepts (j/query db "SELECT c.concept_id,
                                    c.concept_type,
                                    c.provider_id,
                                    c.native_id,
                                    c.metadata,
                                    c.format,
                                    c.revision_id,
                                    c.deleted
                                    FROM concept c,
                                    get_concepts_work_area t
                                    WHERE c.concept_id = t.concept_id AND
                                    c.revision_id = t.revision_id")
            concepts (map db-result->concept-map db-concepts)]
        (j/db-do-commands db "DELETE FROM METADATA_DB.get_concepts_work_area")
        (j/db-do-commands db "COMMIT")
        concepts)
      (catch Exception e
        (j/db-do-commands db "ROLLBACK")
        (throw e))))
  
  (save-concept
    [this concept]
    (util/validate-concept concept)
    (let [{:keys [concept-type native-id concept-id provider-id metadata format revision-id]} concept
          returned-revision-id (:revision-id (get-concept-from-db (:db this) concept-id nil))
          latest-revision-id (if returned-revision-id returned-revision-id -1)]
      (when (and revision-id
                 (not= (inc latest-revision-id) revision-id))
        (errors/throw-service-error :conflict
                                    "Expected revision-id of %s got %s"
                                    latest-revision-id
                                    revision-id))
      (save-in-db (:db this) concept-type native-id concept-id provider-id metadata format (inc latest-revision-id) 0)))
  
  (delete-concept
    [this concept-id]
    (if-let [concept (get-concept-from-db (:db this) concept-id nil)]
      (if (util/is-tombstone? concept)
        (:revision-id concept)
        (let [{:keys [concept-type native-id concept-id provider-id format revision-id]} concept]
          (save-in-db (:db this) concept-type native-id concept-id provider-id " " format (inc revision-id) 1)))
      (errors/throw-service-error :not-found
                                  "Concept %s does not exist."
                                  concept-id)))
  
  (force-delete
    [this concept-id revision-id])
  ;; TODO - implement this
  
  (reset
    [this]
    (reset-database this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db
  "Creates the db needed for clojure.java.jdbc library."
  []
  (map->OracleStore {:db {:classname "oracle.jdbc.driver.OracleDriver"
                          :subprotocol "oracle"
                          :subname (format "thin:@%s:%s:%s" db-host db-port db-sid)
                          :user db-username
                          :password db-password}}))
