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
  "Delete everything from the concpet table."
  [db-config]
  (let [db (:db db-config)]
    (j/db-do-commands db "DELETE FROM METADATA_DB.concept")
    (j/db-do-commands db "DELETE FROM METADATA_DB.concept_id")))

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
    [this concept-id-revision-id-tuples])

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
