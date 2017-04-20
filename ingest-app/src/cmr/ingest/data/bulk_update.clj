(ns cmr.ingest.data.bulk-update
  "Stores and retrieves bulk update status and task information."
  (:require
   [cmr.oracle.connection]
   [cmr.common.lifecycle :as lifecycle]
   [clojure.java.jdbc :as j]
   [clojure.edn :as edn]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import javax.sql.rowset.serial.SerialBlob))

(defprotocol BulkUpdateStore
  "Defines a protocol for getting and storing the bulk update status and task-id
  information."
  (get-bulk-update-status [db provider-id])
  (get-bulk-update-task-status [db provider-id task-id])
  (get-bulk-update-task-collection-status [db task-id])
  (get-bulk-update-collection-status [db task-id concept-id])
  (create-and-save-bulk-update-status [db provider-id json-body concept-ids])
  (update-bulk-update-task-status [db provider-id task-id status status-message])
  (update-bulk-update-collection-status [db task-id concept-id status status-message]))

(defrecord InMemoryBulkUpdateStore
  [data-atom]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; BulkUpdateStore
  ;
  ; (get-bulk-update-status
  ;   [this provider-id])
  ;
  ; (get-bulk-update-task-status
  ;   [this provider-id task-id])
  ;
  ; (create-and-save-bulk-update-status
  ;   [this provider-id json-body concept-ids])

  ; (update-bulk-update-status
  ;   [this provider-id task-id status status-message])
  ;
  ; (update-bulk-update-collection-status
  ;   [this provider-id task-id concept-id status status-message])

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    this)
  (stop
    [this system]
    this))

(defn create-in-memory-bulk-update-store
  []
  (->InMemoryBulkUpdateStore (atom nil)))

;; Extends the BulkUpdateStore to the oracle store so it will work with oracle.
(extend-protocol BulkUpdateStore
  cmr.oracle.connection.OracleStore

  (get-bulk-update-status
    [db provider-id]
    ;; Returns a list of bulk update statuses for the provider
    (su/query db (su/build (select [:task-id :status :status-message]
                            (from "bulk_update_task_status")
                            (where `(= :provider-id ~provider-id))))))

  (get-bulk-update-task-status
    [db provider-id task-id]
    ;; Returns a status for the particular task
    (proto-repl.saved-values/save 10)
    (su/find-one db (select [:status :status-message]
                     (from "bulk_update_task_status")
                     (where `(and (= :task-id ~task-id)
                                  (= :provider-id ~provider-id))))))

  (get-bulk-update-task-collection-status
    [db task-id]
    ;; Get statuses for all collections by task id
    (su/query db (su/build (select [:concept-id :status :status-message]
                            (from "bulk_update_coll_status")
                            (where `(= :task-id ~task-id))))))

  (get-bulk-update-collection-status
    [db task-id concept-id]
    ;; Get the status for a particular collection
    (su/find-one db (select [:status :status-message]
                     (from "bulk_update_coll_status")
                     (where `(and (= :task-id ~task-id)
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
            values [task-id provider-id (util/string->gzip-bytes json-body) "In Progress"]]
       (j/db-do-prepared db statement values)
       ;; Write a row to collection status for each concept id
       (apply j/insert! conn
              "bulk_update_coll_status"
              ["task_id" "concept_id" "status"]
              ;; set :transaction? false since we are already inside a transaction
              (concat (map #(vector task-id % "Pending") concept-ids) [:transaction? false]))
       nil))
     (catch Exception e
      (def e e))))

  (update-bulk-update-task-status
    [db provider-id task-id status status-message]
    (try
      (let [statement (str "UPDATE bulk_update_task_status "
                           "SET status = ?, status_message = ?"
                           "WHERE provider_id = ? AND task_id = ?")]
        (j/db-do-prepared db statement [status status-message provider-id task-id]))
      (catch Exception e
        (def e e))))

  (update-bulk-update-collection-status
    [db task-id concept-id status status-message]
    (try
      (let [statement (str "UPDATE bulk_update_coll_status "
                           "SET status = ?, status_message = ?"
                           "WHERE task_id = ? AND concept_id = ?")]
        (j/db-do-prepared db statement [status status-message task-id concept-id]))
      (catch Exception e
        (def e e)))))

(defn context->db
  [context]
  (get-in context [:system :db]))

(defn-timed get-bulk-update-statuses-for-provider
  "Returns bulk update statuses with task ids by provider"
  [context provider-id]
  (get-bulk-update-status (context->db context) provider-id))

(defn-timed get-bulk-update-task-status-for-provider
  [context provider-id task-id]
  (get-bulk-update-task-status (context->db context) provider-id task-id))

(defn-timed create-bulk-update-status
  "Saves the map of provider id acl hash values"
  [context provider-id json-body concept-ids]
  (create-and-save-bulk-update-status (context->db context) provider-id json-body concept-ids))
