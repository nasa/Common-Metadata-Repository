(ns cmr.metadata-db.data.oracle.concepts
  "Provides implementations of the cmr.metadata-db.data.concepts/ConceptStore methods for OracleStore"
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.data.oracle.sql-helper :as sh]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.common.date-time-parser :as p]
            [clj-time.format :as f]
            [clj-time.coerce :as cr]
            [clj-time.core :as t]
            [cmr.common.concepts :as cc]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.data.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore
           java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream
           java.io.ByteArrayOutputStream
           java.sql.Blob
           oracle.sql.TIMESTAMPTZ))

(def INITIAL_CONCEPT_NUM
  "The number to use as the numeric value for the first concept. Chosen to be larger than the current
  largest sequence in Catalog REST in operations which is 1005488460 as of this writing."
  1200000000)

(def EXPIRED_CONCEPTS_BATCH_SIZE
  "The batch size to retrieve expired concepts"
  5000)

(def mime-type->db-format
  "A mapping of mime type strings to the strings they are stored in the database as. The existing ones
  here match what Catalog REST stores and must continue to match that. Adding new ones is allowed
  but do not modify these existing values."
  {mt/echo10   "ECHO10"
   mt/iso-smap "ISO_SMAP"
   mt/iso      "ISO19115"
   mt/dif      "DIF"
   mt/dif10    "DIF10"})

(def db-format->mime-type
  "A mapping of the format strings stored in the database to the equivalent mime type in concepts"
  ;; We add "ISO-SMAP" mapping here to work with data that are bootstrapped or synchronized directly
  ;; from catalog-rest. Since catalog-rest uses ISO-SMAP as the format value in its database and
  ;; CMR bootstrap-app simply copies this format into CMR database, we could have "ISO-SMAP" as
  ;; a format in CMR database.
  (assoc (set/map-invert mime-type->db-format)
         "ISO-SMAP" mt/iso-smap
         ;; We also have to support whatever the original version of the the string Metadata DB originally used.
         "SMAP_ISO" mt/iso-smap))

(defn safe-max
  "Return the maximimum of two numbers, treating nil as the lowest possible number"
  [num1, num2]
  (cond
    (nil? num2)
    num1

    (nil? num1)
    num2

    :else
    (max num1 num2)))


(defn- truncate-highest
  "Return a sequence with the highest top-n values removed from the input sequence. The
  originall order of the sequence may not be preserved."
  [values top-n]
  (drop-last top-n (sort values)))

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

(defn oracle-timestamp->str-time
  "Converts oracle.sql.TIMESTAMP instance into a string representation of the time. Must be called
  within a with-db-transaction block with the connection"
  [db ot]
  (f/unparse (f/formatters :date-time)
             (oracle/oracle-timestamp->clj-time db ot)))

(defn- by-provider
  "Converts the sql condition clause into provider aware condition clause, i.e. adding the provider-id
  condition to the condition clause when the given provider is small; otherwise returns the same
  condition clause. For example:
  `(= :native-id \"blah\") becomes `(and (= :native-id \"blah\") (= :provider-id \"PROV1\"))"
  [{:keys [provider-id small]} base-clause]
  (if small
    (if (= (first base-clause) 'clojure.core/and)
      ;; Insert the provider id clause at the beginning.
      `(and (= :provider-id ~provider-id) ~@(rest base-clause))
      `(and (= :provider-id ~provider-id) ~base-clause))
    base-clause))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Multi methods for concept types to implement
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti db-result->concept-map
  "Translate concept result returned from db into a concept map"
  (fn [concept-type db provider-id result]
    concept-type))

(defmulti concept->insert-args
  "Converts a concept into the insert arguments based on the concept-type and small field of the provider"
  (fn [concept small]
    [(:concept-type concept) small]))

(defmulti after-save
  "This is a handler for concept types to add save specific behavior after the save of a concept has
  been completed. It will be called after a concept has been saved within the transaction."
  (fn [db provider concept]
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
       :format (db-format->mime-type format)
       :revision-id (int revision_id)
       :revision-date (oracle-timestamp->str-time db revision_date)
       :deleted (not= (int deleted) 0)})))

(defn concept->common-insert-args
  "Converts a concept into a set of insert arguments that is common for all provider concept types."
  [concept]
  (let [{:keys [concept-type
                native-id
                concept-id
                provider-id
                metadata
                format
                revision-id
                revision-date
                deleted]} concept
        fields ["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"]
        values [native-id
                concept-id
                (string->gzip-bytes metadata)
                (mime-type->db-format format)
                revision-id
                deleted]]
    (if revision-date
      [(cons "revision_date" fields)
       (cons (cr/to-sql-time (p/parse-datetime revision-date)) values)]
      [fields values])))

(defmethod after-save :default
  [db provider concept]
  ;; Does nothing
  nil)

(defn- save-concepts-to-tmp-table
  "Utility method to save a bunch of concepts to a temporary table. Must be called from within
  a transaction."
  [conn concept-id-revision-id-tuples]
  (apply j/insert! conn
         "get_concepts_work_area"
         ["concept_id" "revision_id"]
         ;; set :transaction? false since we are already inside a transaction, and
         ;; we want the temp table to persist for the main select
         (concat concept-id-revision-id-tuples [:transaction? false])))

(defn get-latest-concept-id-revision-id-tuples
  "Finds the latest pairs of concept id and revision ids for the given concept ids. If no revisions
  exist for a given concept id it will not be returned."
  [db concept-type provider concept-ids]
  (let [start (System/currentTimeMillis)]
    (j/with-db-transaction
      [conn db]
      ;; use a temporary table to insert our values so we can use a join to
      ;; pull everything in one select
      (apply j/insert! conn
             "get_concepts_work_area"
             ["concept_id"]
             ;; set :transaction? false since we are already inside a transaction, and
             ;; we want the temp table to persist for the main select
             (concat (map vector concept-ids) [:transaction? false]))
      ;; pull back all revisions of each concept-id and then take the latest
      ;; instead of using group-by in SQL
      (let [table (tables/get-table-name provider concept-type)
            stmt (su/build (select [:c.concept-id :c.revision-id]
                                   (from (as (keyword table) :c)
                                         (as :get-concepts-work-area :t))
                                   (where `(and (= :c.concept-id :t.concept-id)))))
            cid-rid-maps (su/query conn stmt)
            concept-id-to-rev-id-maps (map #(hash-map (:concept_id %) (long (:revision_id %)))
                                           cid-rid-maps)
            latest (apply merge-with safe-max {}
                          concept-id-to-rev-id-maps)
            concept-id-revision-id-tuples (seq latest)
            latest-time (- (System/currentTimeMillis) start)]
        (debug (format "Retrieving latest revision-ids took [%d] ms" latest-time))
        concept-id-revision-id-tuples))))

(defn validate-concept-id-native-id-not-changing
  "Validates that the concept-id native-id pair for a concept being saved is not changing. This
  should be done within a save transaction to avoid race conditions where we might miss it.
  Returns nil if valid and an error response if invalid."
  [db provider concept]
  (let [{:keys [concept-type provider-id concept-id native-id]} concept
        table (tables/get-table-name provider concept-type)
        {:keys [concept_id native_id]} (or (su/find-one db (select [:concept-id :native-id]
                                                                   (from table)
                                                                   (where (by-provider provider `(= :native-id ~native-id)))))
                                           (su/find-one db (select [:concept-id :native-id]
                                                                   (from table)
                                                                   (where `(= :concept-id ~concept-id)))))]
    (when (and (and concept_id native_id)
               (or (not= concept_id concept-id) (not= native_id native-id)))
      {:error :concept-id-concept-conflict
       :error-message (format (str "Concept id [%s] and native id [%s] to save do not match "
                                   "existing concepts with concept id [%s] and native id [%s].")
                              concept-id native-id concept_id native_id)
       :existing-concept-id concept_id
       :existing-native-id native_id})))

(extend-protocol c/ConceptsStore
  OracleStore

  (generate-concept-id
    [db concept]
    (let [{:keys [concept-type provider-id]} concept
          seq-num (:nextval (first (su/query db ["SELECT concept_id_seq.NEXTVAL FROM DUAL"])))]
      (cc/build-concept-id {:concept-type concept-type
                            :provider-id provider-id
                            :sequence-number (biginteger seq-num)})))

  (get-concept-id
    [db concept-type provider native-id]
    (let [table (tables/get-table-name provider concept-type)]
      (:concept_id
        (su/find-one db (select [:concept-id]
                                (from table)
                                (where (by-provider provider `(= :native-id ~native-id))))))))

  (get-concept-by-provider-id-native-id-concept-type
    [db provider concept]
    (j/with-db-transaction
      [conn db]
      (let [{:keys [concept-type provider-id native-id revision-id]} concept
            table (tables/get-table-name provider concept-type)
            stmt (if revision-id
                   ;; find specific revision
                   (select '[*]
                           (from table)
                           (where (by-provider provider `(and (= :native-id ~native-id)
                                                              (= :revision-id ~revision-id)))))
                   ;; find latest
                   (select '[*]
                           (from table)
                           (where (by-provider provider `(= :native-id ~native-id)))
                           (order-by (desc :revision-id))))]
        (db-result->concept-map concept-type conn provider-id
                                (su/find-one conn stmt)))))

  (get-concept
    ([db concept-type provider concept-id]
     (j/with-db-transaction
       [conn db]
       (let [table (tables/get-table-name provider concept-type)]
         (db-result->concept-map concept-type conn (:provider-id provider)
                                 (su/find-one conn (select '[*]
                                                           (from table)
                                                           (where `(= :concept-id ~concept-id))
                                                           (order-by (desc :revision-id))))))))
    ([db concept-type provider concept-id revision-id]
     (if revision-id
       (let [table (tables/get-table-name provider concept-type)]
         (j/with-db-transaction
           [conn db]
           (db-result->concept-map concept-type conn (:provider-id provider)
                                   (su/find-one conn (select '[*]
                                                             (from table)
                                                             (where `(and (= :concept-id ~concept-id)
                                                                          (= :revision-id ~revision-id))))))))
       (c/get-concept db concept-type provider concept-id))))

  (get-concepts
    [db concept-type provider concept-id-revision-id-tuples]
    (if (> (count concept-id-revision-id-tuples) 0)
      (let [start (System/currentTimeMillis)]
        (j/with-db-transaction
          [conn db]
          ;; use a temporary table to insert our values so we can use a join to
          ;; pull everything in one select
          (save-concepts-to-tmp-table conn concept-id-revision-id-tuples)

          (let [provider-id (:provider-id provider)
                table (tables/get-table-name provider concept-type)
                stmt (su/build (select [:c.*]
                                       (from (as (keyword table) :c)
                                             (as :get-concepts-work-area :t))
                                       (where `(and (= :c.concept-id :t.concept-id)
                                                    (= :c.revision-id :t.revision-id)))))

                result (doall (map (partial db-result->concept-map concept-type conn provider-id)
                                   (su/query conn stmt)))
                millis (- (System/currentTimeMillis) start)]
            (debug (format "Getting [%d] concepts took [%d] ms" (count result) millis))
            result)))
      []))

  (get-latest-concepts
    [db concept-type provider concept-ids]
    (c/get-concepts
      db concept-type provider
      (get-latest-concept-id-revision-id-tuples db concept-type provider concept-ids)))

  (save-concept
    [db provider concept]
    (try
      (j/with-db-transaction
        [conn db]
        (if-let [error (validate-concept-id-native-id-not-changing db provider concept)]
          ;; There was a concept id, native id mismatch with earlier concepts
          error
          ;; Concept id native id pair was valid
          (let [{:keys [concept-type provider-id]} concept
                table (tables/get-table-name provider concept-type)
                seq-name (str table "_seq")
                [cols values] (concept->insert-args concept (:small provider))
                stmt (format "INSERT INTO %s (id, %s) VALUES (%s.NEXTVAL,%s)"
                             table
                             (str/join "," cols)
                             seq-name
                             (str/join "," (repeat (count values) "?")))]
            ;; Uncomment to debug what's inserted
            ; (debug "Executing" stmt "with values" (pr-str values))
            (j/db-do-prepared db stmt values)
            (after-save conn provider concept)

            nil)))
      (catch Exception e
        (let [error-message (.getMessage e)
              error-code (cond
                           (re-find #"unique constraint.*_CID_REV" error-message)
                           :revision-id-conflict

                           (re-find #"unique constraint.*_CON_REV" error-message)
                           :revision-id-conflict

                           :else
                           :unknown-error)]
          {:error error-code :error-message error-message :throwable e}))))

  (force-delete
    [this concept-type provider concept-id revision-id]
    (let [table (tables/get-table-name provider concept-type)
          stmt (su/build (delete table
                                 (where `(and (= :concept-id ~concept-id)
                                              (= :revision-id ~revision-id)))))]
      (j/execute! this stmt)))

  (force-delete-by-params
    [db provider params]
    (sh/force-delete-concept-by-params db provider params))

  (force-delete-concepts
    [db provider concept-type concept-id-revision-id-tuples]
    (let [table (tables/get-table-name provider concept-type)]
      (j/with-db-transaction
        [conn db]
        ;; use a temporary table to insert our values so we can use them in our delete
        (save-concepts-to-tmp-table conn concept-id-revision-id-tuples)

        (let [stmt [(format "DELETE FROM %s t1 WHERE EXISTS
                            (SELECT 1 FROM get_concepts_work_area tmp WHERE
                            tmp.concept_id = t1.concept_id AND
                            tmp.revision_id = t1.revision_id)"
                            table)]]
          (j/execute! conn stmt)))))

  (get-concept-type-counts-by-collection
    [db concept-type provider]
    (let [table (tables/get-table-name provider :granule)
          {:keys [provider-id small]} provider
          stmt (if small
                 [(format "select count(1) concept_count, a.parent_collection_id
                          from %s a,
                          (select concept_id, max(revision_id) revision_id
                          from %s where provider_id = '%s' group by concept_id) b
                          where  a.deleted = 0
                          and    a.concept_id = b.concept_id
                          and    a.revision_id = b.revision_id
                          group by a.parent_collection_id"
                          table table provider-id)]
                 [(format "select count(1) concept_count, a.parent_collection_id
                          from %s a,
                          (select concept_id, max(revision_id) revision_id
                          from %s group by concept_id) b
                          where  a.deleted = 0
                          and    a.concept_id = b.concept_id
                          and    a.revision_id = b.revision_id
                          group by a.parent_collection_id"
                          table table)])
          result (su/query db stmt)]
      (reduce (fn [count-map {:keys [parent_collection_id concept_count]}]
                (assoc count-map parent_collection_id (long concept_count)))
              {}
              result)))

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
            {:keys [provider-id small]} provider
            stmt (if small
                   [(format "select *
                            from %s outer,
                            ( select a.concept_id, a.revision_id
                            from (select concept_id, max(revision_id) as revision_id
                            from %s
                            where provider_id = '%s'
                            and deleted = 0
                            and   delete_time is not null
                            and   delete_time < systimestamp
                            group by concept_id
                            ) a,
                            (select concept_id, max(revision_id) as revision_id
                            from %s
                            where provider_id = '%s'
                            group by concept_id
                            ) b
                            where a.concept_id = b.concept_id
                            and   a.revision_id = b.revision_id
                            and   rowNum <= %d
                            ) inner
                            where outer.concept_id = inner.concept_id
                            and   outer.revision_id = inner.revision_id"
                            table table provider-id table provider-id EXPIRED_CONCEPTS_BATCH_SIZE)]
                   [(format "select *
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
                            table table table EXPIRED_CONCEPTS_BATCH_SIZE)])]
        (doall (map (partial db-result->concept-map concept-type conn (:provider-id provider))
                    (su/query conn stmt))))))

  (get-tombstoned-concept-revisions
    [this provider concept-type tombstone-cut-off-date limit]
    (j/with-db-transaction
      [conn this]
      (let [table (tables/get-table-name provider concept-type)
            {:keys [provider-id small]} provider
            ;; This will return the concept-id/revision-id pairs for tombstones and revisions
            ;; older than the tombstone - up to 'limit' concepts.
            sql (if small
                  (format "select t1.concept_id, t1.revision_id from %s t1 inner join
                          (select * from
                          (select concept_id, revision_id from %s
                          where provider_id = '%s' and DELETED = 1 and REVISION_DATE < ?)
                          where rownum < %d) t2
                          on t1.concept_id = t2.concept_id and t1.REVISION_ID <= t2.revision_id"
                          table table provider-id limit)
                  (format "select t1.concept_id, t1.revision_id from %s t1 inner join
                          (select * from
                          (select concept_id, revision_id from %s
                          where DELETED = 1 and REVISION_DATE < ?)
                          where rownum < %d) t2
                          on t1.concept_id = t2.concept_id and t1.REVISION_ID <= t2.revision_id"
                          table table limit))
            stmt [sql (cr/to-sql-time tombstone-cut-off-date)]
            result (su/query conn stmt)]
        ;; create tuples of concept-id/revision-id to remove
        (map (fn [{:keys [concept_id revision_id]}]
               [concept_id revision_id])
             result))))

  (get-old-concept-revisions
    [this provider concept-type max-revisions limit]
    (j/with-db-transaction
      [conn this]
      (let [table (tables/get-table-name provider concept-type)
            {:keys [provider-id small]} provider
            ;; This will return the concepts that have more than 'max-revisions' revisions.
            ;; Note: the 'where rownum' clause limits the number of concept-ids that are returned,
            ;; not the number of concept-id/revision-id pairs. All revisions are returned for
            ;; each returned concept-id.
            stmt (if small
                   [(format "select concept_id, revision_id from %s
                            where concept_id in (select * from
                            (select concept_id from %s where provider_id = '%s' group by
                            concept_id having count(*) > %d) where rownum <= %d)"
                            table table provider-id max-revisions limit)]
                   [(format "select concept_id, revision_id from %s
                            where concept_id in (select * from
                            (select concept_id from %s group by
                            concept_id having count(*) > %d) where rownum <= %d)"
                            table table max-revisions limit)])
            result (su/query conn stmt)
            ;; create a map of concept-ids to sequences of all returned revisions
            concept-id-rev-ids-map (reduce (fn [memo concept-map]
                                             (let [{:keys [concept_id revision_id]} concept-map]
                                               (update-in memo [concept_id] conj revision_id)))
                                           {}
                                           result)]
        ;; generate tuples of concept-id/revision-id to remove
        (reduce-kv (fn [memo concept-id rev-ids]
                     (apply merge memo (map (fn [revision-id]
                                              [concept-id revision-id])
                                            ;; only add tuples for old revisions
                                            (truncate-highest rev-ids max-revisions))))
                   []
                   concept-id-rev-ids-map)))))

