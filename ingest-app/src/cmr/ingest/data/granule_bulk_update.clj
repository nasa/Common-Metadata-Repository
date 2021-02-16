(ns cmr.ingest.data.granule-bulk-update
  "Stores and retrieves granule bulk update status and task information."
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
 [num-failed-granules]
 (when (not= 0 num-failed-granules)
   (str num-failed-granules " FAILED")))

(defn- generate-skipped-msg
 "Generate the SKIPPED part of the status message."
 [num-failed-granules num-skipped-granules num-total-granules]
 (when (not= 0 num-skipped-granules)
   (if (not= 0 num-failed-granules)
     (if (not= num-total-granules (+ num-failed-granules num-skipped-granules))
       (str ", " num-skipped-granules " SKIPPED")
       (str " and " num-skipped-granules " SKIPPED"))
     (str num-skipped-granules " SKIPPED"))))

(defn- generate-updated-msg
 "Generate the UPDATED part of the status message."
 [num-failed-granules num-skipped-granules num-total-granules]
 (let [num-updated-granules (- num-total-granules
                                  (+ num-failed-granules num-skipped-granules))]
   (when (not= 0 num-updated-granules)
     (if (or (not= 0 num-failed-granules) (not= 0 num-skipped-granules))
       (str " and " num-updated-granules " UPDATED")
       (str num-updated-granules " UPDATED")))))

(defn generate-task-status-message
 "Generate overall status message based on number of granule failures and skips."
 [num-failed-granules num-skipped-granules num-total-granules]
 (if (and (= 0 num-failed-granules) (= 0 num-skipped-granules))
   "All granule updates completed successfully."
   (str "Task completed with "
        (generate-failed-msg num-failed-granules)
        (generate-skipped-msg num-failed-granules num-skipped-granules num-total-granules)
        (generate-updated-msg num-failed-granules num-skipped-granules num-total-granules)
        " out of "
        num-total-granules
        " total granule update(s).")))

(defprotocol BulkUpdateStore
 "Defines a protocol for getting and storing the bulk update status and task-id
 information."
 (get-provider-bulk-update-status [db provider-id])
 (get-bulk-update-task-status [db task-id provider-id])
 (get-bulk-update-task-granule-status [db task-id])
 (get-bulk-update-granule-status [db task-id concept-id])
 (create-and-save-bulk-update-status [db provider-id json-body granule-urs])
 (update-bulk-update-task-status [db task-id status status-message])
 (update-bulk-update-granule-status [db task-id concept-id status status-message])
 (reset-bulk-update [db]))

(def bulk_update_task_status
  "bulk_update_gran_status")

;; Extends the BulkUpdateStore to the oracle store so it will work with oracle.
(extend-protocol BulkUpdateStore
 cmr.oracle.connection.OracleStore

 (get-provider-bulk-update-status
   [db provider-id]
   (j/with-db-transaction
     [conn db]
     ;; Returns a list of bulk update statuses for the provider
     (let [stmt (su/build (su/select [:created-at :name :task-id :status :status-message :instruction]
                                     (su/from bulk_update_task_status)
                                     (su/where `(= :provider-id ~provider-id))))
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
             (su/find-one (su/select [:created-at :name :status :status-message :instruction]
                                     (su/from bulk_update_task_status)
                                     (su/where `(and (= :task-id ~task-id)
                                                     (= :provider-id ~provider-id)))))
             util/map-keys->kebab-case
             (update :request-json-body util/gzip-blob->string)
             (update :created-at (partial oracle/oracle-timestamp->str-time conn)))))

 (get-bulk-update-task-granule-status
   [db task-id]
   ;; Get statuses for all granules by task id
   (map util/map-keys->kebab-case
        (su/query db (su/build (su/select [:granule-ur :status :status-message]
                                          (su/from "bulk_update_gran_status")
                                          (su/where `(= :task-id ~task-id)))))))

 (get-bulk-update-granule-status
   [db task-id granule-ur]
   ;; Get the status for a particular granule
   (su/find-one db (su/select [:status :status-message]
                              (su/from "granule_bulk_update_tasks")
                              (su/where `(and (= :task-id ~task-id)
                                              (= :granule-ur ~granule-ur))))))

 (create-and-save-bulk-update-status
   [db provider-id json-body granule-urs]
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
       ;; Write a row to granule status for each concept id
       (apply j/insert! conn
              "granule_bulk_update_tasks"
              ["task_id" "granule-ur" "status"]
              ;; set :transaction? false since we are already inside a transaction
              (concat (map #(vector task-id % "PENDING") granule-urs) [:transaction? false]))
       task-id)))

 (update-bulk-update-task-status
   [db task-id status status-message]
   (try
     (let [statement (str "UPDATE bulk_update_gran_status "
                          "SET status = ?, status_message = ?"
                          "WHERE task_id = ?")]
       (j/db-do-prepared db statement [status status-message task-id]))
     (catch Exception e
       (errors/throw-service-error :invalid-data
                                   [(str "Error creating updating bulk update task status "
                                         (.getMessage e))]))))

 (update-bulk-update-granule-status
   [db task-id granule-ur status status-message]
   (try
     (j/with-db-transaction
      [conn db]
      (let [statement (str "UPDATE granule_bulk_update_tasks "
                           "SET status = ?, status_message = ?"
                           "WHERE task_id = ? AND granule_ur = ?")
            status-message (util/trunc status-message 4000)]
        (j/db-do-prepared db statement [status status-message task-id granule-ur])
        (let [task-granules (su/query db
                                         (su/build (su/select
                                                    [:granule-ur :status]
                                                    (su/from "bulk_update_gran_status")
                                                    (su/where `(= :task-id ~task-id)))))
              pending-granules (filter #(= "PENDING" (:status %)) task-granules)
              failed-granules (filter #(= "FAILED" (:status %)) task-granules)
              skipped-granules (filter #(= "SKIPPED" (:status %)) task-granules)]
          (when-not (seq pending-granules)
            (update-bulk-update-task-status db task-id "COMPLETE"
                                            (generate-task-status-message
                                             (count failed-granules)
                                             (count skipped-granules)
                                             (count task-granules)))))))
     (catch Exception e
       (errors/throw-service-error :invalid-data
                                   [(str "Error creating updating bulk update granule status "
                                         (.getMessage e))]))))

 (reset-bulk-update
   [db]
   (let [statement "DELETE FROM granule_bulk_update_tasks"]
     (j/db-do-prepared db statement []))
   (let [statement "DELETE FROM granule_bulk_update_tasks"]
     (j/db-do-prepared db statement []))))

(defn context->db
 [context]
 (get-in context [:system :db]))

(defn-timed get-bulk-update-statuses-for-provider
 "Returns bulk update statuses with task ids by provider"
 [context provider-id]
 (get-provider-bulk-update-status (context->db context) provider-id))

(defn-timed get-bulk-update-task-status-for-provider
 [context task-id provider-id]
 (get-bulk-update-task-status (context->db context) task-id provider-id))

(defn-timed get-bulk-update-granule-statuses-for-task
 [context task-id]
 (get-bulk-update-task-granule-status (context->db context) task-id))

(defn-timed create-bulk-update-task
 "Creates all the rows for bulk update status tables - task status and granule
 status. Returns task id"
 [context provider-id instruction granule-urs]
 (create-and-save-bulk-update-status
  (context->db context)
  provider-id
  instruction
  granule-urs))

(defn-timed create-granule-bulk-update-task
 "Create all rows for granule bulk update status tables - task status and granule status.
 Returns task id."
 [context provider-id instruction granule-urs]
 (create-and-save-bulk-update-status
  (context->db context)
  provider-id
  instruction
  granule-urs))


(defn-timed update-bulk-update-task-granule-status
 "For the task and concept id, update the granule to the given status with the
 given status message"
 [context task-id granule-ur status status-message]
 (update-bulk-update-granule-status
  (context->db context)
  task-id
  granule-ur
  status
  status-message))

(defn reset-db
 "Clear bulk update db"
 [context]
 (reset-bulk-update (context->db context)))

(defn cleanup-old-bulk-update-status
 "Delete rows in the bulk_update_gran_status table that are older than the
 configured age"
 [context]
 (let [db (context->db context)
       statement (str "delete from CMR_INGEST.bulk_update_gran_status "
                      "where created_at < (current_timestamp - INTERVAL '"
                      (config/bulk-update-cleanup-minimum-age)
                      "' DAY)")]
   (j/db-do-prepared db statement)))

(comment
 (reset-bulk-update (context->db context))
 (def db (context->db context)))
