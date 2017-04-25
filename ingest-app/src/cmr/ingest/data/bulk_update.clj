(ns cmr.ingest.data.bulk-update
  "Stores and retrieves bulk update status and task information."
  (:require
   [clojure.java.jdbc :as j]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.oracle.connection]
   [cmr.oracle.sql-utils :as su]))

(defprotocol BulkUpdateStore
  "Defines a protocol for getting and storing the bulk update status and task-id
  information."
  (get-provider-bulk-update-status [db provider-id])
  (get-bulk-update-task-status [db task-id])
  (get-bulk-update-task-collection-status [db task-id])
  (get-bulk-update-collection-status [db task-id concept-id])
  (create-and-save-bulk-update-status [db provider-id json-body concept-ids])
  (update-bulk-update-task-status [db task-id status status-message])
  (update-bulk-update-collection-status [db task-id concept-id status status-message])
  (reset-bulk-update [db]))

;; Extends the BulkUpdateStore to the oracle store so it will work with oracle.
(extend-protocol BulkUpdateStore
  cmr.oracle.connection.OracleStore

  (get-provider-bulk-update-status
    [db provider-id]
    ;; Returns a list of bulk update statuses for the provider
    (su/query db (su/build (su/select [:task-id :status :status-message]
                            (su/from "bulk_update_task_status")
                            (su/where `(= :provider-id ~provider-id))))))

  (get-bulk-update-task-status
    [db task-id]
    ;; Returns a status for the particular task
    (su/find-one db (su/select [:status :status-message]
                     (su/from "bulk_update_task_status")
                     (su/where `(= :task-id ~task-id)))))

  (get-bulk-update-task-collection-status
    [db task-id]
    ;; Get statuses for all collections by task id
    (su/query db (su/build (su/select [:concept-id :status :status-message]
                            (su/from "bulk_update_coll_status")
                            (su/where `(= :task-id ~task-id))))))

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
    (try
     (j/with-db-transaction
      [conn db]
      (let [task-id (:nextval (first (su/query db ["SELECT task_id_seq.NEXTVAL FROM DUAL"])))
            statement (str "INSERT INTO bulk_update_task_status "
                           "(task_id, provider_id, request_json_body, status)"
                           "VALUES (?, ?, ?, ?)")
            values [task-id provider-id (util/string->gzip-bytes json-body) "IN_PROGRESS"]]
       (j/db-do-prepared db statement values)
       ;; Write a row to collection status for each concept id
       (apply j/insert! conn
              "bulk_update_coll_status"
              ["task_id" "concept_id" "status"]
              ;; set :transaction? false since we are already inside a transaction
              (concat (map #(vector task-id % "PENDING") concept-ids) [:transaction? false]))
       nil))
     (catch Exception e
      (errors/throw-service-error :invalid-data
       [(str "Error creating creating bulk update status "
             (.getMessage e))]))))

  (update-bulk-update-task-status
    [db provider-id task-id status status-message]
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
      (let [statement (str "UPDATE bulk_update_coll_status "
                           "SET status = ?, status_message = ?"
                           "WHERE task_id = ? AND concept_id = ?")]
        (j/db-do-prepared db statement [status status-message task-id concept-id]))
      (catch Exception e
        (errors/throw-service-error :invalid-data
         [(str "Error creating updating bulk update collection status "
               (.getMessage e))]))))

  (reset-bulk-update
    [db]
    (let [statement "DELETE FROM bulk_update_coll_status"]
      (j/db-do-prepared db statement []))
    (let [statement "DELETE FROM bulk_update_task_status"]
      (j/db-do-prepared db statement []))))

(defn context->db
  [context]
  (get-in context [:system :db]))

(defn-timed get-bulk-update-statuses-for-provider
  "Returns bulk update statuses with task ids by provider"
  [context provider-id]
  (get-provider-bulk-update-status (context->db context) provider-id))

(defn-timed get-bulk-update-task-status-for-provider
  [context task-id]
  (get-bulk-update-task-status (context->db context) task-id))

(defn-timed create-bulk-update-status
  "Saves the map of provider id acl hash values"
  [context provider-id json-body concept-ids]
  (create-and-save-bulk-update-status (context->db context) provider-id json-body concept-ids))
