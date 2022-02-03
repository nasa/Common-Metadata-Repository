(ns cmr.ingest.data.bulk-update
  "Stores and retrieves bulk update status and task information."
  (:require
   [cheshire.core :as json]
   [clojure.java.jdbc :as j]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.ingest.config :as config]
   [cmr.oracle.connection]
   [cmr.oracle.connection :as oracle]
   [cmr.oracle.sql-utils :as su]))

(defn- generate-failed-msg
  "Generate the FAILED part of the status message."
  [num-failed-collections]
  (when (not= 0 num-failed-collections)
    (str num-failed-collections " FAILED")))

(defn- generate-skipped-msg
  "Generate the SKIPPED part of the status message."
  [num-failed-collections num-skipped-collections num-total-collections]
  (when (not= 0 num-skipped-collections)
    (if (not= 0 num-failed-collections)
      (if (not= num-total-collections (+ num-failed-collections num-skipped-collections))
        (str ", " num-skipped-collections " SKIPPED")
        (str " and " num-skipped-collections " SKIPPED"))
      (str num-skipped-collections " SKIPPED"))))

(defn- generate-updated-msg
  "Generate the UPDATED part of the status message."
  [num-failed-collections num-skipped-collections num-total-collections]
  (let [num-updated-collections (- num-total-collections
                                   (+ num-failed-collections num-skipped-collections))]
    (when (not= 0 num-updated-collections)
      (if (or (not= 0 num-failed-collections) (not= 0 num-skipped-collections))
        (str " and " num-updated-collections " UPDATED")
        (str num-updated-collections " UPDATED")))))

(defn generate-task-status-message
  "Generate overall status message based on number of collection failures and skips."
  [num-failed-collections num-skipped-collections num-total-collections]
  (if (and (= 0 num-failed-collections) (= 0 num-skipped-collections))
    "All collection updates completed successfully."
    (str "Task completed with "
         (generate-failed-msg num-failed-collections)
         (generate-skipped-msg num-failed-collections num-skipped-collections num-total-collections)
         (generate-updated-msg num-failed-collections num-skipped-collections num-total-collections)
         " out of "
         num-total-collections
         " total collection update(s).")))

(defprotocol BulkUpdateStore
  "Defines a protocol for getting and storing the bulk update status and task-id
  information."
  (get-provider-bulk-update-status [db provider-id])
  (get-bulk-update-task-status [db task-id provider-id])
  (get-bulk-update-task-collection-status [db task-id])
  (get-bulk-update-collection-status [db task-id concept-id])
  (create-and-save-bulk-update-status [db provider-id json-body concept-ids])
  (update-bulk-update-task-status [db task-id status status-message])
  (update-bulk-update-collection-status [db task-id concept-id status status-message])
  (reset-bulk-update [db]))

(def bulk_update_task_status
 "bulk_update_task_status")

;; Extends the BulkUpdateStore to the oracle store so it will work with oracle.
(extend-protocol BulkUpdateStore
  cmr.oracle.connection.OracleStore

  (get-provider-bulk-update-status
   [db provider-id]
   (j/with-db-transaction
    [conn db]
    ;; Returns a list of bulk update statuses for the provider
    (let [stmt (su/build (su/select [:created-at :name :task-id :status :status-message :request-json-body]
                                    (su/from bulk_update_task_status)
                                    (su/where `(= :provider-id ~provider-id))
                                    (su/order-by (su/desc `(+ :task-id 0)))))
          ;; Note: the column selected out of the database is created_at, instead of created-at.
          statuses (doall (map #(update % :created_at (partial oracle/oracle-timestamp->str-time conn))
                               (su/query conn stmt)))
          statuses (map util/map-keys->kebab-case statuses)]
      (map #(update % :request-json-body util/gzip-blob->string)
           statuses))))

  (get-bulk-update-task-status
   [db task-id provider-id]
   (j/with-db-transaction
    [conn db]
    ;; Returns a status for the particular task
    (some-> conn
            (su/find-one (su/select [:created-at :name :status :status-message :request-json-body]
                                    (su/from bulk_update_task_status)
                                    (su/where `(and (= :task-id ~task-id)
                                                    (= :provider-id ~provider-id)))))
            util/map-keys->kebab-case
            (update :request-json-body util/gzip-blob->string)
            (update :created-at (partial oracle/oracle-timestamp->str-time conn)))))

  (get-bulk-update-task-collection-status
   [db task-id]
   ;; Get statuses for all collections by task id
   (map util/map-keys->kebab-case
        (su/query db (su/build (su/select [:concept-id :status :status-message]
                                          (su/from "bulk_update_coll_status")
                                          (su/where `(= :task-id ~task-id)))))))

  (get-bulk-update-collection-status
   [db task-id concept-id]
   ;; Get the status for a particular collection
   (su/find-one db (su/select [:status :status-message]
                              (su/from "bulk_update_coll_status")
                              (su/where `(and (= :task-id ~task-id)
                                              (= :concept-id ~concept-id))))))

  (create-and-save-bulk-update-status
   [db provider-id json-body concept-ids]
   ;; In a transaction, add one row to the task status table and for each concept
   (j/with-db-transaction
    [conn db]
    (let [task-id (:nextval (first (su/query db ["SELECT task_id_seq.NEXTVAL FROM DUAL"])))
          statement (str "INSERT INTO bulk_update_task_status "
                         "(task_id, provider_id, name, request_json_body, status)"
                         "VALUES (?, ?, ?, ?, ?)")
          name (get (json/parse-string json-body) "name" task-id)
          values [task-id provider-id name (util/string->gzip-bytes json-body) "IN_PROGRESS"]]
      (j/db-do-prepared db statement values)
      ;; Write a row to collection status for each concept id
      (apply j/insert! conn
             "bulk_update_coll_status"
             ["task_id" "concept_id" "status"]
             ;; set :transaction? false since we are already inside a transaction
             (concat (map #(vector task-id % "PENDING") concept-ids) [:transaction? false]))
      task-id)))

  (update-bulk-update-task-status
   [db task-id status status-message]
   (try
     (let [statement (str "UPDATE bulk_update_task_status "
                          "SET status = ?, status_message = ?"
                          "WHERE task_id = ?")]
       (j/db-do-prepared db statement [status status-message task-id]))
     (catch Exception e
       (errors/throw-service-error :invalid-data
                                   [(str "Error creating updating bulk update task status "
                                         (.getMessage e))]))))

  (update-bulk-update-collection-status
   [db task-id concept-id status status-message]
   (try
     (j/with-db-transaction
      [conn db]
      (let [statement (str "UPDATE bulk_update_coll_status "
                           "SET status = ?, status_message = ?"
                           "WHERE task_id = ? AND concept_id = ?")
            status-message (util/trunc status-message 4000)]
        (j/db-do-prepared db statement [status status-message task-id concept-id])
        (let [task-collections (su/query db
                                         (su/build (su/select
                                                    [:concept-id :status]
                                                    (su/from "bulk_update_coll_status")
                                                    (su/where `(= :task-id ~task-id)))))
              pending-collections (filter #(= "PENDING" (:status %)) task-collections)
              failed-collections (filter #(= "FAILED" (:status %)) task-collections)
              skipped-collections (filter #(= "SKIPPED" (:status %)) task-collections)]
          (when-not (seq pending-collections)
            (update-bulk-update-task-status db task-id "COMPLETE"
                                            (generate-task-status-message
                                             (count failed-collections)
                                             (count skipped-collections)
                                             (count task-collections)))))))
     (catch Exception e
       (errors/throw-service-error :invalid-data
                                   [(str "Error creating updating bulk update collection status "
                                         (.getMessage e))]))))

  (reset-bulk-update
   [db]
   (su/run-sql db "DELETE FROM bulk_update_coll_status")
   (su/run-sql db "DELETE FROM bulk_update_task_status")
   (su/run-sql db "ALTER SEQUENCE task_id_seq restart start with 1")))

(defn context->db
  [context]
  (get-in context [:system :db]))

(defn-timed get-collection-tasks
  "Returns bulk update statuses with task ids by provider"
  [context provider-id]
  (get-provider-bulk-update-status (context->db context) provider-id))

(defn-timed get-bulk-update-task-status-for-provider
  [context task-id provider-id]
  (get-bulk-update-task-status (context->db context) task-id provider-id))

(defn-timed get-bulk-update-collection-statuses-for-task
  [context task-id]
  (get-bulk-update-task-collection-status (context->db context) task-id))

(defn-timed create-bulk-update-task
  "Creates all the rows for bulk update status tables - task status and collection
  status. Returns task id"
  [context provider-id json-body concept-ids]
  (create-and-save-bulk-update-status (context->db context) provider-id json-body concept-ids))

(defn-timed update-bulk-update-task-collection-status
  "For the task and concept id, update the collection to the given status with the
  given status message"
  [context task-id concept-id status status-message]
  (update-bulk-update-collection-status (context->db context) task-id concept-id
    status status-message))

(defn reset-db
  "Clear bulk update db"
  [context]
  (reset-bulk-update (context->db context)))

(defn cleanup-old-bulk-update-status
  "Delete rows in the bulk-update-task-status table that are older than the configured age"
  [context]
  (let [db (context->db context)
        statement (str "delete from CMR_INGEST.bulk_update_task_status "
                       "where created_at < (current_timestamp - INTERVAL '"
                       (config/bulk-update-cleanup-minimum-age)
                       "' DAY)")]
    (j/db-do-prepared db statement)))

(comment
  (reset-bulk-update (context->db context))
  (def db (context->db context)))
