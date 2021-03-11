(ns cmr.ingest.data.granule-bulk-update
  "Stores and retrieves granule bulk update status and task information."
  (:require
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

(defprotocol GranBulkUpdateStore
  "Defines a protocol for getting and storing the granule bulk update status and task-id
  information."
  (get-provider-bulk-granule-update-status [db provider-id])
  (get-bulk-granule-update-task-status [db task-id provider-id])
  (get-bulk-update-task-granule-status [db task-id])
  (get-bulk-update-granule-status [db task-id concept-id])
  (create-and-save-bulk-granule-update-status [db provider-id user-id request-json-body instructions])
  (update-bulk-granule-update-task-status [db task-id status status-message])
  (update-bulk-update-granule-status [db task-id granule-ur status status-message])
  (reset-bulk-granule-update [db]))

(def bulk_update_task_status
  "bulk_update_gran_status")

;; Extends the GranBulkUpdateStore to the oracle store so it will work with oracle.
(extend-protocol GranBulkUpdateStore
  cmr.oracle.connection.OracleStore

  (get-provider-bulk-update-status
   [db provider-id]
   (jdbc/with-db-transaction
    [conn db]
    ;; Returns a list of bulk update statuses for the provider
    (let [stmt (sql-utils/build (sql-utils/select [:created-at :name :task-id :status :status-message :instruction]
                                                  (sql-utils/from bulk_update_task_status)
                                                  (sql-utils/where `(= :provider-id ~provider-id))))
          ;; Note: the column selected out of the database is created_at, instead of created-at.
          statuses (doall (map #(update % :created_at (partial oracle/oracle-timestamp->str-time conn))
                               (sql-utils/query conn stmt)))
          statuses (map util/map-keys->kebab-case statuses)]
      (map #(update % :request-json-body util/gzip-blob->string)
           statuses))))

  (get-bulk-granule-update-task-status
   [db task-id provider-id]
   (jdbc/with-db-transaction
    [conn db]
    ;; Returns a status for the particular task
    (some-> conn
            (sql-utils/find-one (sql-utils/select [:created-at :name :status :status-message :instruction]
                                                  (sql-utils/from bulk_update_task_status)
                                                  (sql-utils/where `(and (= :task-id ~task-id))
                                                                   (= :provider-id ~provider-id))))
            util/map-keys->kebab-case
            (update :request-json-body util/gzip-blob->string)
            (update :created-at (partial oracle/oracle-timestamp->str-time conn)))))

  (get-bulk-update-task-granule-status
   [db task-id]
   ;; Get statuses for all granules by task id
   (map util/map-keys->kebab-case
        (sql-utils/query db (sql-utils/build (sql-utils/select [:granule-ur :status :status-message])
                                             (sql-utils/from "bulk_update_gran_status")
                                             (sql-utils/where `(= :task-id ~task-id))))))

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
                         "(task_id, provider_id, name, request_json_body, status, user_id)"
                         "VALUES (?, ?, ?, ?, ?, ?)")
          parsed-json (json/parse-string request-json-body true)
          task-name (get parsed-json :name task-id)
          unique-task-name (format "%s: %s" task-name task-id)
          values [task-id provider-id unique-task-name (util/string->gzip-bytes request-json-body)
                  "IN_PROGRESS" user-id]]
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
        (jdbc/db-do-prepared db statement [status status-message task-id granule-ur])
        (let [task-granules (sql-utils/query
                             db
                             (sql-utils/build (sql-utils/select
                                               [:granule-ur :status]
                                               (sql-utils/from "bulk_update_gran_status")
                                               (sql-utils/where `(= :task-id ~task-id)))))
              pending-granules (filter #(= "PENDING" (:status %)) task-granules)]
          (when-not (seq pending-granules)
            (let [failed-granules (filter #(= "FAILED" (:status %)) task-granules)
                  skipped-granules (filter #(= "SKIPPED" (:status %)) task-granules)]
              (update-bulk-granule-update-task-status db task-id "COMPLETE"
                                                      (generate-task-status-message
                                                       (count failed-granules)
                                                       (count skipped-granules)
                                                       (count task-granules))))))))
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

(defn-timed get-bulk-update-statuses-for-provider
  "Returns bulk update statuses with task ids by provider"
  [context provider-id]
  (get-provider-bulk-granule-update-status (context->db context) provider-id))

(defn-timed get-bulk-update-task-status-for-provider
  [context task-id provider-id]
  (get-bulk-granule-update-task-status (context->db context) task-id provider-id))

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

(defn reset-db
  "Clear bulk update db"
  [context]
  (reset-bulk-granule-update (context->db context)))

(defn cleanup-old-bulk-update-status
  "Delete rows in the bulk_update_gran_status table that are older than the
  configured age"
  [context]
  (let [db (context->db context)
        statement (str "delete from CMR_INGEST.bulk_update_gran_status "
                       "where created_at < (current_timestamp - INTERVAL '"
                       (config/bulk-update-cleanup-minimum-age)
                       "' DAY)")]
    (jdbc/db-do-prepared db statement)))
