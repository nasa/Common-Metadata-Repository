(ns cmr.metadata-db.data.oracle
  "Contains a record definition that implements the ConcpetStore and Lifecycle protocols
  backed by an Oracle database."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.utility :as util]
            [cmr.metadata-db.data.messages :as messages]
            [slingshot.slingshot :refer [throw+]]))

;;; Constants

(def concept-id-prefix-length 1)

(def db-username (get (System/getenv) "MDB_DB_USERNAME" "METADATA_DB"))
(def db-password (get (System/getenv) "MDB_DB_PASSWORD" "METADATA_DB"))
(def db-host (get (System/getenv) "MDB_DB_HOST" "localhost"))
(def db-port (get (System/getenv) "MDB_DB_PORT" "1521"))
(def db-sid (get (System/getenv) "MDB_DB_SID" "orcl"))

;;; Utility methods

(defn reset-database
  "Delete everything from the concept table and reset the sequence."
  [db-config]
  (let [db (:db db-config)]
    (j/db-do-commands db "DELETE FROM METADATA_DB.concept")
    (try (j/db-do-commands db "DROP SEQUENCE METADATA_DB.concept_id_seq")
      (catch Exception e)) ; don't care if the sequence was not there
    (j/db-do-commands db "CREATE SEQUENCE METADATA_DB.concept_id_seq
                         START WITH 1000000000
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

(defn- generate-concept-id
  "Create a concept-id for a given concept type and provider id."
  [db concept]
  (let [{:keys [concept-type provider-id]} concept
        seq-num (:nextval (first (j/query db ["SELECT METADATA_DB.concept_id_seq.NEXTVAL FROM DUAL"])))]
    (util/generate-concept-id concept-type provider-id seq-num)))


;;; TODO - the next two functons probably should be consolidated

(defn- get-concept-from-db
  "Load a concept from the database using a concept-id and revision-id and then transform it into a concept map."
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

(defn- get-concept-from-db-with-values
  "Load a concept from the database using concept-type, provider-id, native-id, and optional revision-id
  then transform it into a concept map."
  [db concept]
  (let [{:keys [concept-type provider-id native-id revision-id]} concept] 
    (if revision-id
      (db-result->concept-map (first (j/query db ["SELECT concept_type, native_id, concept_id, provider_id, metadata, format, revision_id, deleted
                                                  FROM METADATA_DB.concept
                                                  WHERE concept_type = ? 
                                                  AND provider_id = ?
                                                  AND native_id = ?
                                                  AND revision_id = ?"
                                                  concept-type
                                                  provider-id
                                                  native-id
                                                  revision-id])))
      (db-result->concept-map (first (j/query db ["SELECT concept_type, native_id, concept_id, provider_id, metadata, format, revision_id, deleted
                                                  FROM METADATA_DB.concept
                                                  WHERE concept_type= ?
                                                  AND provider_id = ?
                                                  AND native_id = ?
                                                  ORDER BY revision_id DESC" 
                                                  concept-type
                                                  provider-id
                                                  native-id]))))))

(defn- get-existing-concept-id
  "Retrieve concept-id from DB."
  [db concept]
  (some-> (get-concept-from-db-with-values db concept)
          :concept-id))

(defn- save-in-db
  "Saves the concept in database and returns the revision-id"
  [db concept]
  (try (let [{:keys [concept-type native-id concept-id provider-id metadata format revision-id deleted]} concept]
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
         {:concept-id concept-id :revision-id revision-id})
    (catch Exception e
      (let [error-message (.getMessage e)
            error-code (cond
                         (re-find #"METADATA_DB.UNIQUE_CONCEPT_REVISION" error-message)
                         :concept-id-concept-conflict
                         
                         (re-find #"METADATA_DB.UNIQUE_CONCEPT_ID_REVISION" error-message)
                         :revision-id-conflict
                         
                         :else
                         :unknown-error)]
        {:error error-code}))))

(defn- set-or-generate-concept-id 
  "Get an exiting concept-if from the DB for the given concept or generate one 
  if the concept has never been save."
  [db concept]
  (if (:concept-id concept) 
    concept
    (let [concept-id (get-existing-concept-id db concept)]
      (if concept-id
        (assoc concept :concept-id concept-id)
        (assoc concept :concept-id (generate-concept-id db concept))))))

(defn- set-or-generate-revision-id
  "Get the next available revision id from the DB for the given concept or
  zero if the concept has never been saved."
  [db concept]
  (if (:revision-id concept)
    concept
    (let [existing-revision-id (:revision-id (get-concept-from-db db (:concept-id concept) nil))
          revision-id (if existing-revision-id (inc existing-revision-id) 0)]
      (assoc concept :revision-id revision-id))))

;;; TODO move this to the services layer
(defn- validate-concept-revision-id
  "Validate that the revision-id for a concept (if given) is one greater than
  the current maximum revision-id for this concept."
  [db concept]
  (let [{:keys [concept-id revision-id]} concept]
    (if revision-id
      (if concept-id
        (let [latest-revision (get-concept-from-db db concept-id nil)
              expected-revision-id (inc (:revision-id latest-revision))]
          (when (not= revision-id expected-revision-id)
            (errors/throw-service-error :conflict
                                        (format messages/invalid-revision-id-msg
                                                expected-revision-id
                                                revision-id))))
        (if (not= revision-id 0)
          (errors/throw-service-error :conflict
                                      (format messages/invalid-revision-id-msg
                                              0
                                              revision-id)))))))

(defn try-to-save
  "Try to save a concept by looping until we find a good revision-id or give up."
  [db concept revision-id-provided?]
  (loop [concept concept tries-left 3]
    (let [result (save-in-db db concept)]
      (if (nil? (:error result))
        result
        ;; depending on the error we will either throw an exception or try again (recur)
        (let [error-code (:error result)] 
          (cond 
            (= error-code :revision-id-conflict)
            (if revision-id-provided?
              (errors/throw-service-error :conflict (format messages/invalid-revision-id-unknown-expected-msg
                                                            revision-id-provided?))
              (if (= tries-left 1)
                (errors/internal-error! messages/maximum-save-attempts-exceeded-msg)))
            
            (= error-code :concept-id-concept-conflict)
            (let [{:keys [concept-id concept-type provider-id native-id]} concept]
              (errors/throw-service-error :conflict (format messages/concept-exists-with-differnt-id-msg
                                                            concept-id
                                                            concept-type
                                                            provider-id
                                                            native-id)))
            
            (= error-code :unknown-error)
            (errors/internal-error! "Unknown error saving concept")
            
            ; FIXME there might be a case where two first revision of a collection come in at the same time
            ;; they might accidentally get different concept ids. We'd need to recur then.
            )
          (recur (set-or-generate-revision-id db concept) (dec tries-left)))))))

(defn- set-deleted-flag [value concept] (assoc concept :deleted value))

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
        (errors/throw-service-error :not-found messages/missing-concept-id-msg
                                    concept-type
                                    provider-id
                                    native-id))))
  
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
    (validate-concept-revision-id db concept)
    (let [db (:db this)
          concept-id-provided? (:concept-id concept)
          revision-id-provided? (:revision-id concept)
          concept (->> concept
                       (set-or-generate-concept-id db)
                       (set-or-generate-revision-id db)
                       (set-deleted-flag 0))]
      (try-to-save db concept revision-id-provided?)))
  
  (delete-concept
    [this concept-id revision-id]
    (let [db (:db this)
          last-concept-saved (get-concept-from-db db concept-id nil)]
      (if last-concept-saved
        (if (util/is-tombstone? last-concept-saved)
          last-concept-saved
          (let [tombstone (merge last-concept-saved {:revision-id revision-id :deleted true})]
            (validate-concept-revision-id db tombstone)
            (let [revisioned-tombstone (set-or-generate-revision-id db tombstone)]
              (try-to-save db revisioned-tombstone revision-id))))
        (errors/throw-service-error :not-found
                                    (format messages/concept-does-not-exist-msg
                                            concept-id)))))
  
  
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
