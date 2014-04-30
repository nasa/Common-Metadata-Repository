(ns cmr.metadata-db.data.oracle.concepts
  "Provides default implementations of the cmr.metadata-db.data.concepts multimethods"
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [cmr.common.services.errors :as errors]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [sqlingvo.core :as s :refer [select from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.common.concepts :as cc])
  (:import cmr.metadata_db.data.oracle.core.OracleStore
           java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream
           java.io.ByteArrayOutputStream
           java.sql.Blob))

(def INITIAL_CONCEPT_NUM
  "The number to use as the numeric value for the first concept."
  1000000000)


(defn blob->input-stream
  "Convert a BLOB to an InputStream"
  [^Blob blob]
  (.getBinaryStream blob))


(defn blob->string
  "Convert a BLOB to a string"
  [blob]
  (-> blob blob->input-stream GZIPInputStream. slurp))

(defn string->gzip-bytes
  "Convert a string to an array of compressed bytes"
  [input]
  (let [output (ByteArrayOutputStream.)
        gzip (GZIPOutputStream. output)]
    (io/copy input gzip)
    (.finish gzip)
    (.toByteArray output)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Multi methods for concept types to implement
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti db-result->concept-map
  "Translate concept result returned from db into a concept map"
  (fn [concept-type provider-id result]
    concept-type))

(defmulti concept->insert-args
  "Converts a concept into the insert arguments"
  (fn [concept]
    (:concept-type concept)))

(defmulti after-save
  "This is a handler for concept types to add save specific behavior after the save of a concept has
  been completed. It will be called after a concept has been saved within the transaction."
  (fn [db concept]
    (:concept-type concept)))


;; Multimethod defaults

(defmethod db-result->concept-map :default
  [concept-type provider-id result]
  (when result
    (let [{:keys [native_id
                  concept_id
                  metadata
                  format
                  revision_id
                  deleted]} result]
      {:concept-type concept-type
       :native-id native_id
       :concept-id concept_id
       :provider-id provider-id
       :metadata (blob->string metadata)
       :format format
       :revision-id (int revision_id)
       :deleted (not= (int deleted) 0)})))

(defmethod concept->insert-args :default
  [concept]
  (let [{:keys [concept-type
                native-id
                concept-id
                provider-id
                metadata
                format
                revision-id
                deleted]} concept]
    [["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"]
     [native-id concept-id (string->gzip-bytes metadata) format revision-id deleted]]))

(defmethod after-save :default
  [db concept]
  ;; Does nothing
  nil)


(defn find-params->sql-clause
  "Converts a parameter map for finding concept types into a sql clause for inclusion in a query."
  [params]
  ;; Validate parameter names as a sanity check to prevent sql injection
  (let [valid-param-name #"^[a-zA-Z][a-zA-Z0-9_\-]*$"]
    (when-let [invalid-names (seq (filter #(not (re-matches valid-param-name (name %))) (keys params)))]
      (errors/internal-error! (format "Attempting to search with invalid parameter names [%s]"
                                      (str/join ", " invalid-names)))))
  (let [comparisons (for [[k v] params]
                      `(= ~k ~v))]
    (if (> (count comparisons) 1)
      (cons `and comparisons)
      (first comparisons))))

(extend-protocol c/ConceptsStore
  OracleStore

  (generate-concept-id
    [db concept]
    (let [{:keys [concept-type provider-id]} concept
          seq-num (:nextval (first (j/query db ["SELECT concept_id_seq.NEXTVAL FROM DUAL"])))]
      (cc/build-concept-id {:concept-type concept-type
                            :provider-id provider-id
                            :sequence-number (biginteger seq-num)})))

  (get-concept-id
    [db concept-type provider-id native-id]
    (let [table (tables/get-table-name provider-id concept-type)]
      (:concept_id
        (su/find-one db (select [:concept-id]
                          (from table)
                          (where `(= :native-id ~native-id)))))))

  (get-concept-by-provider-id-native-id-concept-type
    [db concept]
    (let [{:keys [concept-type provider-id native-id revision-id]} concept
          table (tables/get-table-name provider-id concept-type)
          stmt (if revision-id
                 ;; find specific revision
                 (select '[*]
                   (from table)
                   (where `(and (= :native-id ~native-id)
                                (= :revision-id ~revision-id))))
                 ;; find latest
                 (select '[*]
                   (from table)
                   (where `(= :native-id ~native-id))
                   (order-by (desc :revision-id))))]
      (db-result->concept-map concept-type provider-id
                              (su/find-one db stmt))))

  (get-concept
    ([db concept-type provider-id concept-id]
     (let [table (tables/get-table-name provider-id concept-type)]
       (db-result->concept-map concept-type provider-id
                               (su/find-one db (select '[*]
                                                 (from table)
                                                 (where `(= :concept-id ~concept-id))
                                                 (order-by (desc :revision-id)))))))
    ([db concept-type provider-id concept-id revision-id]
     (if revision-id
       (let [table (tables/get-table-name provider-id concept-type)]
         (db-result->concept-map concept-type provider-id
                                 (su/find-one db (select '[*]
                                                   (from table)
                                                   (where `(and (= :concept-id ~concept-id)
                                                                (= :revision-id ~revision-id)))))))
       (c/get-concept db concept-type provider-id concept-id))))

  (get-concepts
    [db concept-type provider-id concept-id-revision-id-tuples]
    (j/with-db-transaction
      [conn db]
      ;; use a temporary table to insert our values so we can use a join to
      ;; pull everything in one select
      (apply j/insert! conn
             "get_concepts_work_area"
             ["concept_id" "revision_id"]
             (concat concept-id-revision-id-tuples [:transaction false]))
      (let [table (tables/get-table-name provider-id concept-type)
            stmt (su/build (select [:c.*]
                             (from (as (keyword table) :c)
                                   (as :get-concepts-work-area :t))
                             (where `(and (= :c.concept-id :t.concept-id)
                                          (= :c.revision-id :t.revision-id)))))]
        (map (partial db-result->concept-map concept-type provider-id)
             (j/query conn stmt)))))

  (find-concepts
    [db params]
    (let [{:keys [concept-type provider-id]} params
          params (dissoc params :concept-type :provider-id)
          table (tables/get-table-name provider-id concept-type)
          stmt (su/build (select [:*]
                           (from table)
                           (where (find-params->sql-clause params))))]
      (map (partial db-result->concept-map concept-type provider-id)
           (j/query db stmt))))


  (save-concept
    [db concept]
    (try
      (j/with-db-transaction
        [conn db]
        (let [{:keys [concept-type provider-id]} concept
              table (tables/get-table-name provider-id concept-type)]
          (apply j/insert! conn table (concept->insert-args concept))
          (after-save conn concept)

          nil))
      (catch Exception e
        (let [error-message (.getMessage e)
              error-code (cond
                           ;;TODO we should have unit tests for this
                           (re-find #"unique constraint.*_CID_REV" error-message)
                           :concept-id-concept-conflict

                           (re-find #"unique constraint.*_CON_REV" error-message)
                           :revision-id-conflict

                           :else
                           :unknown-error)]
          {:error error-code :error-message error-message}))))

  (force-delete
    [this concept-type provider-id concept-id revision-id]
    (let [table (tables/get-table-name provider-id concept-type)
          stmt (su/build (delete table
                           (where `(and (= :concept-id ~concept-id)
                                        (= :revision-id ~revision-id)))))]
      (j/execute! this stmt)))

  (force-delete-by-params
    [db params]
    (let [{:keys [concept-type provider-id]} params
          params (dissoc params :concept-type :provider-id)
          table (tables/get-table-name provider-id concept-type)
          stmt (su/build (delete table
                           (where (find-params->sql-clause params))))]
      (j/execute! db stmt)))

  (reset
    [this]
    (try
      (j/db-do-commands this "DROP SEQUENCE concept_id_seq")
      (catch Exception e)) ; don't care if the sequence was not there
    (j/db-do-commands this (format "CREATE SEQUENCE concept_id_seq
                                   START WITH %d
                                   INCREMENT BY 1
                                   CACHE 20" INITIAL_CONCEPT_NUM))))




