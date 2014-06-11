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
            [clj-time.format :as f]
            [clj-time.coerce :as cr]
            [clj-time.core :as t]
            [cmr.common.concepts :as cc]
            [cmr.oracle.connection]
            [cmr.metadata-db.data.oracle.sql-utils :as sql-utils])
  (:import cmr.oracle.connection.OracleStore
           java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream
           java.io.ByteArrayOutputStream
           java.sql.Blob
           com.mchange.v2.c3p0.impl.NewProxyConnection
           oracle.sql.TIMESTAMPTZ))

(def INITIAL_CONCEPT_NUM
  "The number to use as the numeric value for the first concept."
  1000000000)

(def EXPIRED_CONCEPTS_BATCH_SIZE
  "The batch size to retrieve expired concepts"
  5000)

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

(def inner-conn-field
  "Cached class reflection field to retrieve the inner oracle connection from the proxy
  created by connection pooling"
  (let [field (.getDeclaredField com.mchange.v2.c3p0.impl.NewProxyConnection "inner")]
    (.setAccessible field true)
    field))

(defn db->oracle-conn
  "Gets an oracle connection from the outer database connection. Should be called from within as
  with-db-transaction block."
  [db]
  (if-let [proxy-conn (:connection db)]
    (.get inner-conn-field proxy-conn)
    (errors/internal-error!
      (str "Called db->oracle-conn with connection that was not within a db transaction. "
           "It must be called from within call j/with-db-transaction"))))

(defn oracle-timestamp-tz->clj-time
  "Converts oracle.sql.TIMESTAMPTZ instance into a clj-time. Must be called within
  a with-db-transaction block with the connection"
  [db ^oracle.sql.TIMESTAMPTZ ot]
  (let [^java.sql.Connection conn (db->oracle-conn db)
        result (cr/from-sql-time (.timestampValue ot conn))]
    (f/unparse (f/formatters :date-time) result)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Multi methods for concept types to implement
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti db-result->concept-map
  "Translate concept result returned from db into a concept map"
  (fn [concept-type db provider-id result]
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
  [concept-type db provider-id result]
  (when result
    (let [{:keys [native_id
                  concept_id
                  metadata
                  format
                  revision_id
                  revision_date
                  deleted]} result]
      {:concept-type concept-type
       :native-id native_id
       :concept-id concept_id
       :provider-id provider-id
       :metadata (blob->string metadata)
       :format format
       :revision-id (int revision_id)
       :revision-date (oracle-timestamp-tz->clj-time db revision_date)
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
    (j/with-db-transaction
      [conn db]
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
        (db-result->concept-map concept-type conn provider-id
                                (su/find-one conn stmt)))))

  (get-concept
    ([db concept-type provider-id concept-id]
     (j/with-db-transaction
       [conn db]
       (let [table (tables/get-table-name provider-id concept-type)]
         (db-result->concept-map concept-type conn provider-id
                                 (su/find-one conn (select '[*]
                                                           (from table)
                                                           (where `(= :concept-id ~concept-id))
                                                           (order-by (desc :revision-id))))))))
    ([db concept-type provider-id concept-id revision-id]
     (if revision-id
       (let [table (tables/get-table-name provider-id concept-type)]
         (j/with-db-transaction
           [conn db]
           (db-result->concept-map concept-type conn provider-id
                                   (su/find-one conn (select '[*]
                                                             (from table)
                                                             (where `(and (= :concept-id ~concept-id)
                                                                          (= :revision-id ~revision-id))))))))
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

        (doall (map (partial db-result->concept-map concept-type conn provider-id)
                    (j/query conn stmt))))))

  (find-concepts
    [db params]
    (j/with-db-transaction
      [conn db]
      (let [{:keys [concept-type provider-id]} params
            params (dissoc params :concept-type :provider-id)
            table (tables/get-table-name provider-id concept-type)
            stmt (su/build (select [:*]
                                   (from table)
                                   (when-not (empty? params)
                                     (where (find-params->sql-clause params)))))]
        (doall (map (partial db-result->concept-map concept-type conn provider-id)
                    (j/query conn stmt))))))

  (find-concepts-in-batches
    [db params batch-size]

    (letfn [(find-batch
              [start-index]
              (j/with-db-transaction
                [conn db]
                (let [{:keys [concept-type provider-id]} params
                      params (dissoc params :concept-type :provider-id)
                      table (tables/get-table-name provider-id concept-type)
                      istmt (select [:*]
                                    (from table)
                                    (when-not (empty? params)
                                      (where (find-params->sql-clause params)))
                                    (order-by :concept-id))]
                  (doall (map (partial db-result->concept-map concept-type conn provider-id)
                              (sql-utils/find-batch conn istmt start-index batch-size))))))
            (lazy-find
              [start-index]
              (let [batch (find-batch start-index)]
                (when-not (empty? batch)
                  (cons batch (lazy-seq (lazy-find (+ start-index (count batch))))))))]
      (lazy-find 0)))

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
                                   CACHE 20" INITIAL_CONCEPT_NUM)))

  (get-expired-concepts
    [this provider concept-type]
    (j/with-db-transaction
      [conn this]
      (let [table (tables/get-table-name provider concept-type)
            stmt [(format "select *
                          from %s outer,
                          ( select a.concept_id, a.revision_id
                            from (select concept_id, max(revision_id) as revision_id
                                    from %s
                                    where deleted = 0
                                    and   delete_time is not null
                                    and   delete_time < systimestamp
                                    group by concept_id
                                    ) a,
                                    (select concept_id, max(revision_id) as revision_id
                                    from %s
                                    group by concept_id
                                    ) b
                            where a.concept_id = b.concept_id
                            and   a.revision_id = b.revision_id
                            and   rowNum <= %d
                          ) inner
                          where outer.concept_id = inner.concept_id
                          and   outer.revision_id = inner.revision_id"
                          table table table EXPIRED_CONCEPTS_BATCH_SIZE)]]
        (doall (map (partial db-result->concept-map concept-type conn provider)
                    (j/query conn stmt)))))))

