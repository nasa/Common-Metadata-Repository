(ns cmr.ingest.data.granule-bulk-update
  "Stores and retrieves granule bulk update status and task information."
  (:require
   [cmr.common.time-keeper :as time-keeper]
   [clj-time.coerce :as time-coerce]
   [cheshire.core :as json]
   [clojure.java.jdbc :as jdbc]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.ingest.config :as config]
   [cmr.oracle.connection :as oracle]
   [cmr.oracle.sql-utils :as sql-utils]))

(defn- generate-failed-msg
 "Generate the FAILED part of the status message."
 [num-failed-granules]
 (when-not (zero? num-failed-granules)
   (str num-failed-granules " FAILED")))

(defn- generate-skipped-msg
 "Generate the SKIPPED part of the status message."
 [num-failed-granules num-skipped-granules num-total-granules]
 (when-not (zero? num-skipped-granules)
   (if (not (zero? num-failed-granules))
     (if (not= num-total-granules (+ num-failed-granules num-skipped-granules))
       (str ", " num-skipped-granules " SKIPPED")
       (str " and " num-skipped-granules " SKIPPED"))
     (str num-skipped-granules " SKIPPED"))))

(defn- generate-updated-msg
 "Generate the UPDATED part of the status message."
 [num-failed-granules num-skipped-granules num-total-granules]
 (let [num-updated-granules (- num-total-granules
                                  (+ num-failed-granules num-skipped-granules))]
   (when-not (zero? num-updated-granules)
     (if (or (not (zero? num-failed-granules)) (not (zero? num-skipped-granules)))
       (str " and " num-updated-granules " UPDATED")
       (str num-updated-granules " UPDATED")))))

(defn generate-task-status-message
 "Generate overall status message based on number of granule failures and skips."
 [num-failed-granules num-skipped-granules num-total-granules]
 (if (and (zero? num-failed-granules) (zero? num-skipped-granules))
   "All granule updates completed successfully."
   (format "Task completed with %s out of %s total granule update(s)."
           (str (generate-failed-msg num-failed-granules)
                (generate-skipped-msg num-failed-granules num-skipped-granules num-total-granules)
                (generate-updated-msg num-failed-granules num-skipped-granules num-total-granules))
           num-total-granules)))

(defn- instructions->gran-stats
  "Return the bulk_update_gran_status insert values in the form of
   [task_id provider_id granule_ur instruction status] for the given instructions map."
  [task-id provider-id instructions]
  ;; create a granule status row for each granule-ur
  (concat
   (map #(vector task-id provider-id (:granule-ur %) (json/generate-string %) "PENDING")
        instructions)
   ;; set :transaction? false since we are already inside a transaction
   [:transaction? false]))

(defn- normalize-gran-status
  "Returns the normalized granule status"
  [gran-status]
  (-> gran-status
      util/remove-nil-keys
      util/map-keys->kebab-case))

(defprotocol GranBulkUpdateStore
  "Defines a protocol for getting and storing the granule bulk update status and task-id
  information."
  (get-granule-tasks-by-provider-id [db provider-id])
  (get-granule-task-by-task-id [db task-id])
  (get-bulk-update-task-granule-status [db task-id])
  (get-bulk-update-granule-status [db task-id concept-id])
  (create-and-save-bulk-granule-update-status [db provider-id user-id request-json-body instructions])
  (update-bulk-granule-update-task-status [db task-id status status-message])
  (update-bulk-update-granule-status [db task-id granule-ur status status-message])
  (reset-bulk-granule-update [db]))

;; Extends the GranBulkUpdateStore to the oracle store so it will work with oracle.
(extend-protocol GranBulkUpdateStore
  cmr.oracle.connection.OracleStore

  (get-granule-tasks-by-provider-id
   [db provider-id]
   (jdbc/with-db-transaction
    [conn db]
    ;; Returns a list of bulk update tasks for the provider
    (let [stmt (sql-utils/build
                (sql-utils/select
                 [:created-at :name :task-id :status :status-message :request-json-body]
                 (sql-utils/from "granule_bulk_update_tasks")
                 (sql-utils/where `(= :provider-id ~provider-id))
                 (sql-utils/order-by (sql-utils/desc `(+ :task-id 0)))))
          ;; Note: the column selected out of the database is created_at, instead of created-at.
          statuses (doall (map #(update % :created_at (partial oracle/oracle-timestamp->str-time conn))
                               (sql-utils/query conn stmt)))
          statuses (map util/map-keys->kebab-case statuses)]
      (map #(update % :request-json-body util/gzip-blob->string)
           statuses))))

  (get-granule-task-by-task-id
   [db task-id]
   (jdbc/with-db-transaction
    [conn db]
    (some-> conn
            (sql-utils/find-one
             (sql-utils/select
              [:created-at :name :provider-id :status :status-message :request-json-body]
              (sql-utils/from "granule_bulk_update_tasks")
              (sql-utils/where `(= :task-id ~task-id))))
            util/map-keys->kebab-case
            (update :request-json-body util/gzip-blob->string)
            (update :created-at (partial oracle/oracle-timestamp->str-time conn)))))

  (get-bulk-update-task-granule-status
   [db task-id]
   ;; Get statuses for all granules by task id
   (map normalize-gran-status
        (sql-utils/query db (sql-utils/build
                             (sql-utils/select
                              [:granule-ur :status :status-message]
                              (sql-utils/from "bulk_update_gran_status")
                              (sql-utils/where `(= :task-id ~task-id)))))))

  (get-bulk-update-granule-status
   [db task-id granule-ur]
   ;; Get the status for a particular granule
   (sql-utils/find-one db (sql-utils/select [:status :status-message]
                                            (sql-utils/from "granule_bulk_update_tasks")
                                            (sql-utils/where `(and (= :task-id ~task-id))
                                                             (= :granule-ur ~granule-ur)))))

  (create-and-save-bulk-granule-update-status
   [db provider-id user-id request-json-body instructions]
   ;; In a transaction, add one row to the task status table and for each concept
   (jdbc/with-db-transaction
    [conn db]
    (let [task-id (:nextval (first (sql-utils/query db ["SELECT GRAN_TASK_ID_SEQ.NEXTVAL FROM DUAL"])))
          statement (str "INSERT INTO granule_bulk_update_tasks "
                         "(task_id, provider_id, name, request_json_body, status, user_id, created_at)"
                         "VALUES (?, ?, ?, ?, ?, ?, ?)")
          parsed-json (json/parse-string request-json-body true)
          task-name (get parsed-json :name task-id)
          unique-task-name (format "%s: %s" task-name task-id)
          created-at (time-coerce/to-sql-time (time-keeper/now))
          values [task-id provider-id unique-task-name (util/string->gzip-bytes request-json-body)
                  "IN_PROGRESS" user-id created-at]]
      (jdbc/db-do-prepared db statement values)
      ;; Write a row to granule status for each granule-ur
      (apply jdbc/insert! conn
             "bulk_update_gran_status"
             ["task_id" "provider_id" "granule_ur" "instruction" "status"]
             (instructions->gran-stats task-id provider-id instructions))
      task-id)))

  (update-bulk-granule-update-task-status
   [db task-id status status-message]
   (try
     (let [statement (str "UPDATE granule_bulk_update_tasks "
                          "SET status = ?, status_message = ?"
                          "WHERE task_id = ?")]
       (jdbc/db-do-prepared db statement [status status-message task-id]))
     (catch Exception e
       (errors/throw-service-error :invalid-data
                                   [(str "Error updating bulk update task status "
                                         (.getMessage e))]))))

  (update-bulk-update-granule-status
    [db task-id granule-ur status status-message]
    (try
      (jdbc/with-db-transaction
        [conn db]
        (let [statement (str "UPDATE bulk_update_gran_status "
                             "SET status = ?, status_message = ?"
                             "WHERE task_id = ? AND granule_ur = ?")
              status-message (util/trunc status-message 4000)]
          (jdbc/db-do-prepared db statement [status status-message task-id granule-ur])))
      (catch Exception e
        (error "Exception caught in update bulk update granule status: " e)
        (errors/throw-service-error :invalid-data
                                    [(str "Error updating bulk update granule status "
                                          (.getMessage e))]))))

  (reset-bulk-granule-update
   [db]
   (sql-utils/run-sql db "DELETE FROM bulk_update_gran_status")
   (sql-utils/run-sql db "DELETE FROM granule_bulk_update_tasks")
   (sql-utils/run-sql db "ALTER SEQUENCE gran_task_id_seq restart start with 1")))

(defn context->db
 "Return the path to the database from a given context"
 [context]
 (get-in context [:system :db]))

(defn-timed cleanup-bulk-granule-tasks
  "Run a delete operation in the database to delete bulk granule update
  tasks older than the retention period."
  [context]
  (try
    ;; Deletes will cascade to granule_bulk_update_tasks
    (jdbc/delete!
     (context->db context)
     :granule_bulk_update_tasks
     ["STATUS = 'COMPLETE' AND CREATED_AT < SYSDATE - ?"
      (config/granule-bulk-cleanup-minimum-age)])
    (catch Exception e
      (error "Exception caught while attempting to clean up granule bulk update task table: " e)
      (errors/throw-service-error :invalid-data
                                  [(str "Error cleaning up bulk granule update task table "
                                        (.getMessage e))]))))

(defn-timed get-granule-tasks-by-provider
  "Returns granule bulk update tasks by provider"
  [context provider-id]
  (get-granule-tasks-by-provider-id (context->db context) provider-id))

(defn-timed get-granule-task-by-id
  "Returns the granule bulk update task by id"
  [context task-id]
  (get-granule-task-by-task-id (context->db context) task-id))

(defn-timed get-bulk-update-granule-statuses-for-task
  [context task-id]
  (get-bulk-update-task-granule-status (context->db context) task-id))

(defn-timed create-granule-bulk-update-task
  "Create all rows for granule bulk update status tables - task status and granule status.
  Returns task id."
  [context provider-id user-id request-json-body instructions]
  (create-and-save-bulk-granule-update-status
   (context->db context) provider-id user-id request-json-body instructions))

(defn-timed update-bulk-update-task-granule-status
  "For the task and concept id, update the granule to the given status with the
   given status message"
  [context task-id granule-ur status status-message]
  (when-not (= "UPDATED" status)
    (info (format "Granule update %s with message: %s" status status-message)))
  (update-bulk-update-granule-status
   (context->db context) task-id granule-ur status status-message))

(defn-timed get-incomplete-granule-task-ids
  "Returns a list of granule bulk update task ids where the status is not COMPELTE."
  [context]
  (let [db (context->db context)
        vals (sql-utils/query
              db
              (sql-utils/build
               (sql-utils/select
                [:task-id]
                (sql-utils/from "granule_bulk_update_tasks")
                ;; purposely not using `not=` since sqlingvo doesn't understand it
                (sql-utils/where `(not (= :status "COMPLETE"))))))]
    ;; sql returns with underscores, not dash
    (map :task_id vals)))

(defn validate-task-exists
  "Validates the task exists in the database."
  [context task-id]
  (when-not (sql-utils/find-one
             (context->db context)
             (sql-utils/select
              [:task-id]
              (sql-utils/from "granule_bulk_update_tasks")
              (sql-utils/where `(= :task-id ~task-id))))
    (errors/throw-service-errors
     :not-found
     [(format "No granule bulk granule update task with ID [%s] found."
              task-id)]))
  task-id)

(defn-timed task-completed?
  "Returns false if there are any granule updates marked PENDING."
  [context task-id]
  (validate-task-exists context task-id)
  (let [db (context->db context)]
    (nil? (sql-utils/find-one
           db
           (sql-utils/select
            [:granule-ur :status]
            (sql-utils/from "bulk_update_gran_status")
            (sql-utils/where `(and (= :status "PENDING")
                                   (= :task-id ~task-id))))))))

(defn-timed mark-task-complete
  "Marks a granule bulk task as COMPLETE and sets the status message.
  It will throw an exception if there still granules marked as PENDING."
  [context task-id]
  (let [db (context->db context)
        task-granules (sql-utils/query
                       db
                       (sql-utils/build
                        (sql-utils/select
                         [:granule-ur :status]
                         (sql-utils/from "bulk_update_gran_status")
                         (sql-utils/where `(= :task-id ~task-id)))))
        pending-granules (filter #(= "PENDING" (:status %)) task-granules)
        failed-granules (filter #(= "FAILED" (:status %)) task-granules)
        skipped-granules (filter #(= "SKIPPED" (:status %)) task-granules)]
    (if (zero? (count pending-granules))
      (update-bulk-granule-update-task-status db task-id "COMPLETE"
                                              (generate-task-status-message
                                               (count failed-granules)
                                               (count skipped-granules)
                                               (count task-granules)))
      (throw (ex-info
              (str "Tried to mark bulk-granule-update-task as complete "
                   "when there are still granules marked as PENDING.")
              {:task-id task-id
               :pending-granule pending-granules})))))

(defn reset-db
  "Clear bulk update db"
  [context]
  (reset-bulk-granule-update (context->db context)))
