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
            [cmr.metadata-db.data.utility :as util])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource))

;;; Constants

(def concept-id-prefix-length 1)

(def select-all-columns-str "SELECT concept_type, native_id, concept_id, 
                            provider_id, metadata, format, revision_id, 
                            deleted FROM METADATA_DB.concept ")

;;; Utility methods

(defn reset-database
  "Delete everything from the concept table and reset the sequence."
  [db-config]
  )

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
  
  (generate-concept-id
    [this concept]
    (let [{:keys [concept-type provider-id]} concept
          seq-num (:nextval (first (j/query this ["SELECT METADATA_DB.concept_id_seq.NEXTVAL FROM DUAL"])))]
      (util/generate-concept-id concept-type provider-id seq-num)))
  
  
  (get-concept-id
    [this concept-type provider-id native-id]
    (let [concept-id (first (j/query this ["SELECT concept_id
                                           FROM METADATA_DB.concept
                                           WHERE concept_type = ?
                                           AND provider_id = ?
                                           AND native_id = ?"
                                           concept-type
                                           provider-id
                                           native-id]))]
      concept-id))
  
  (get-concept-by-provider-id-native-id-concept-type 
    [this concept]
    (let [{:keys [concept-type provider-id native-id revision-id]} concept] 
      (if revision-id
        (db-result->concept-map (first (j/query this [(str select-all-columns-str
                                                           "WHERE concept_type = ? 
                                                           AND provider_id = ?
                                                           AND native_id = ?
                                                           AND revision_id = ?")
                                                      concept-type
                                                      provider-id
                                                      native-id
                                                      revision-id])))
        (db-result->concept-map (first (j/query this [(str select-all-columns-str
                                                           "WHERE concept_type= ?
                                                           AND provider_id = ?
                                                           AND native_id = ?
                                                           ORDER BY revision_id DESC" )
                                                      concept-type
                                                      provider-id
                                                      native-id]))))))
  
  (get-concept
    [this concept-id revision-id]
    (if revision-id
      (db-result->concept-map (first (j/query this [(str select-all-columns-str
                                                         "WHERE concept_id = ? AND revision_id = ?")
                                                    concept-id
                                                    revision-id])))
      (db-result->concept-map (first (j/query this [(str select-all-columns-str
                                                         "WHERE concept_id = ?
                                                         ORDER BY revision_id DESC") concept-id])))))
  
  (get-concepts
    [this concept-id-revision-id-tuples]
    (j/with-db-transaction [conn this]
                           ;; use a temporary table to insert our values so we can use a join to 
                           ;; pull evertying in one select
                           (let [insert-args (conj (conj concept-id-revision-id-tuples :transaction) false)]
                             (apply j/insert! conn
                                    "METADATA_DB.get_concepts_work_area"
                                    ["concept_id" 
                                     "revision_id"]
                                    insert-args))
                           (let [db-concepts (j/query conn "SELECT c.concept_id,
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
                                                           c.revision_id = t.revision_id")]
                             (map db-result->concept-map db-concepts))))
  
  (save
    [this concept]
    (try (let [{:keys [concept-type native-id concept-id provider-id metadata format revision-id deleted]} concept]
           (j/insert! this 
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
  
  
  (force-delete
    [this concept-id revision-id]
    (j/execute! this 
                ["DELETE FROM METADATA_DB.concept WHERE concept_id = ? and revision_id = ?"
                 concept-id
                 revision-id]))
  
  (reset
    [this]
    (let [db this]
      (j/db-do-commands db "DELETE FROM METADATA_DB.concept")
      (try 
        (j/db-do-commands db "DROP SEQUENCE METADATA_DB.concept_id_seq")
        (catch Exception e)) ; don't care if the sequence was not there
      (j/db-do-commands db "CREATE SEQUENCE METADATA_DB.concept_id_seq
                           START WITH 1000000000
                           INCREMENT BY 1
                           CACHE 20"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec)) 
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))] 
    {:datasource cpds}))

(defn create-db
  "Creates the db needed for clojure.java.jdbc library."
  [db-spec]
  (map->OracleStore (pool db-spec)))
