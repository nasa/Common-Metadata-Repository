(ns cmr.metadata-db.data.oracle.concepts
  "Provides default implementations of the cmr.metadata-db.data.concepts multimethods"
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :refer [select from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]))

(defmethod c/db-result->concept-map :default
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
       :metadata metadata
       :format format
       :revision-id (int revision_id)
       :deleted (not= (int deleted) 0)})))

(defmethod c/concept->insert-args :default
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
     [native-id concept-id metadata format revision-id deleted]]))


(defmethod c/get-concept-id :default
  [db concept-type provider-id native-id]
  (let [table (tables/get-table-name provider-id concept-type)]
    (su/find-one db (select [:concept-id]
                      (from table)
                      (where `(= :native-id ~native-id))))))

(defmethod c/get-concept-by-provider-id-native-id-concept-type :default
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
    (c/db-result->concept-map concept-type provider-id
                              (su/find-one db stmt))))

(defmethod c/get-concept :default
  ([db concept-type provider-id concept-id]
   (let [table (tables/get-table-name provider-id concept-type)]
     (c/db-result->concept-map concept-type provider-id
                               (su/find-one db (select '[*]
                                                 (from table)
                                                 (where `(= :concept-id ~concept-id))
                                                 (order-by (desc :revision-id)))))))
  ([db concept-type provider-id concept-id revision-id]
   (if revision-id
     (let [table (tables/get-table-name provider-id concept-type)]
       (c/db-result->concept-map concept-type provider-id
                                 (su/find-one db (select '[*]
                                                   (from table)
                                                   (where `(and (= :concept-id ~concept-id)
                                                                (= :revision-id ~revision-id)))))))
     (c/get-concept db concept-type concept-id))))

(defmethod c/get-concepts :default
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
      (map (partial c/db-result->concept-map concept-type provider-id)
           (j/query conn stmt)))))

(defmethod c/save-concept :default
  [db concept]
  (try
    (let [{:keys [concept-type provider-id]} concept
          table (tables/get-table-name provider-id concept-type)]
      (apply j/insert! db table (c/concept->insert-args concept)))
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

(defmethod c/force-delete :default
  [this concept-type provider-id concept-id revision-id]
  (let [table (tables/get-table-name provider-id concept-type)]
    (j/execute! this (su/build (delete
                                 (from table)
                                 (where `(and (= :concept-id ~concept-id)
                                              (= :revision-id ~revision-id))))))))




