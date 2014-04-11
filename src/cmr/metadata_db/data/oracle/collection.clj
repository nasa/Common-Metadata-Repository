(ns cmr.metadata-db.data.oracle.collection
  (:require [cmr.metadata-db.data.concepts :as concepts]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :as s :refer [select from where with order-by desc delete as]]
            [sqlingvo.vendor :as v]
            [sqlingvo.compiler]
            [sqlingvo.util]))

(defn- db-result->concept-map
  "Translate concept result returned from db into a concept map"
  [result concept-type provider-id]
  (when result
    (let [{:keys [native_id concept_id
                  metadata format revision_id deleted]} result]
      {:concept-type concept-type
       :native-id native_id
       :concept-id concept_id
       :provider-id provider-id
       :metadata metadata
       :format format
       :revision-id (int revision_id)
       :deleted (not= (int deleted) 0)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO put these functions in sql utils
(v/defvendor CmrSqlStyle
             "A defined style for generating sql with sqlingvo that we would use with oracle."
             :name sqlingvo.util/sql-name-underscore
             :keyword sqlingvo.util/sql-keyword-hyphenize
             :quote identity)

;; Replaces the existing compile-sql function to generate table alias's in the Oracle style which doesn't use the AS word.
;; See https://github.com/r0man/sqlingvo/issues/4
(defmethod sqlingvo.compiler/compile-sql :table [db {:keys [as schema name]}]
  [(str (clojure.string/join "." (map #(s/sql-quote db %1) (remove nil? [schema name])))
        (when as (str " " (s/sql-quote db as))))])


(defn build
  "Creates a sql statement vector for clojure.java.jdbc."
  [stmt]
  (s/sql (->CmrSqlStyle) stmt))

(defn find-one
  "TODO"
  [db stmt]
  (let [stmt (with [:inner stmt]
                   (select ['*]
                     (from :inner)
                     (where '(= :ROWNUM 1))))]
    (first (j/query db (build stmt)))))

;; end sql utils functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defmethod concepts/get-concept-id :collection
  [db concept-type provider-id native-id]
  (let [table (tables/get-table-name provider-id :collection)]
    (find-one db (select [:concept-id]
                   (from table)
                   (where `(= :native-id ~native-id))))))

(defmethod concepts/get-concept-by-provider-id-native-id-concept-type :collection
  [db concept]
  (let [{:keys [provider-id native-id revision-id]} concept
        table (tables/get-table-name provider-id :collection)
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
    (db-result->concept-map :collection provider-id
                            (find-one db stmt))))

(defmethod concepts/get-concept :collection
  ([db concept-type provider-id concept-id]
   (let [table (tables/get-table-name provider-id :collection)]
     (db-result->concept-map :collection provider-id
                             (find-one db (select '[*]
                                            (from table)
                                            (where `(= :concept-id ~concept-id))
                                            (order-by (desc :revision-id)))))))
  ([db concept-type provider-id concept-id revision-id]
   (if revision-id
     (let [table (tables/get-table-name provider-id :collection)]
       (db-result->concept-map :collection provider-id
                               (find-one db (select '[*]
                                              (from table)
                                              (where `(and (= :concept-id ~concept-id)
                                                           (= :revision-id ~revision-id)))))))
     (concepts/get-concept db concept-type concept-id))))

(defmethod concepts/get-concepts :collection
  [db concept-type provider-id concept-id-revision-id-tuples]
  (j/with-db-transaction
    [conn db]
    ;; use a temporary table to insert our values so we can use a join to
    ;; pull everything in one select
    (apply j/insert! conn
           "get_concepts_work_area"
           ["concept_id" "revision_id"]
           (concat concept-id-revision-id-tuples [:transaction false]))
    (let [provider-id "PROV1"
          table (tables/get-table-name provider-id :collection)
          stmt (build (select [:c.*]
                        (from (as (keyword table) :c)
                              (as :get-concepts-work-area :t))
                        (where `(and (= :c.concept-id :t.concept-id)
                                     (= :c.revision-id :t.revision-id)))))]
      (map db-result->concept-map (j/query conn stmt)))))

(defmethod concepts/save-concept :collection
  [db concept]
  (try
    (let [{:keys [native-id concept-id provider-id metadata format revision-id deleted]} concept
          table (tables/get-table-name provider-id :collection)]
      (j/insert! db table
                 ["native_id" "concept_id" "metadata" "format" "revision_id" "deleted"]
                 [native-id concept-id metadata format revision-id deleted])
      {:concept-id concept-id :revision-id revision-id})
    (catch Exception e
      (let [error-message (.getMessage e)
            error-code (cond
                         ;;TODO confirm that these error messages are still correct.
                         ;; we should have unit tests for this
                         (re-find #"UNIQUE_CONCEPT_REVISION" error-message)
                         :concept-id-concept-conflict

                         (re-find #"UNIQUE_CONCEPT_ID_REVISION" error-message)
                         :revision-id-conflict

                         :else
                         :unknown-error)]
        {:error error-code :error-message error-message}))))

(defmethod concepts/force-delete :collection
  [this concept-type provider-id concept-id revision-id]
  (let [table (tables/get-table-name provider-id :collection)]
    (j/execute! this (build (delete
                              (from table)
                              (where `(and (= :concept-id ~concept-id)
                                           (= :revision-id ~revision-id))))))))




