(ns cmr.ingest.data.memory-db
  "Stores and retrieves the hashes of the ACLs for a provider."
  (:require
   [clojure.edn :as edn]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.ingest.data.bulk-update :as bulk-update]
   [cmr.ingest.data.provider-acl-hash :as acl-hash]))

(defrecord MemoryDB

  [acl-hash-data-atom task-status-atom collection-status-atom task-id-atom]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  acl-hash/AclHashStore

  (save-acl-hash
    [this acl-hash]
    (reset! (:acl-hash-data-atom this) acl-hash))

  (get-acl-hash
    [this]
    (-> this :acl-hash-data-atom deref))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  bulk-update/BulkUpdateStore

  (get-provider-bulk-update-status
    [this provider-id]
    (some->> @task-status-atom
             (filter #(= provider-id (:provider-id %)))
             (map #(select-keys % [:task-id :status :status-message]))))

  (get-bulk-update-task-status
    [this task-id]
    (let [task-status (some->> @task-status-atom
                               (some #(when (= task-id (:task-id %))
                                            %)))]
      (select-keys task-status [:task-id :status :status-message])))

  (get-bulk-update-task-collection-status
    [this task-id]
    (some->> @collection-status-atom
             (filter #(= task-id (:task-id %)))
             (map #(select-keys % [:concept-id :status :status-message]))))

  (get-bulk-update-collection-status
    [this task-id concept-id]
    (let [coll-status (some->> @collection-status-atom
                               (some #(when (and (= concept-id (:concept-id %))
                                                 (= task-id (:task-id %)))
                                            %)))]
      (select-keys coll-status [:concept-id :status :status-message])))

  (create-and-save-bulk-update-status
    [this provider-id json-body concept-ids]
    (swap! task-id-atom inc)
    (let [task-id @task-id-atom
          task-statuses @task-status-atom
          collection-statuses @collection-status-atom]
     (reset! task-status-atom (conj task-statuses
                                    {:task-id task-id
                                     :provider-id provider-id
                                     :request-json-body json-body
                                     :status "IN_PROGRESS"}))
     (reset! collection-status-atom (concat collection-statuses
                                            (map (fn [c]
                                                  {:task-id task-id
                                                   :concept-id c
                                                   :status "PENDING"})
                                                 concept-ids)))))
  (update-bulk-update-task-status
    [this task-id status status-message]
    (let [task-statuses @task-status-atom
          index (first (keep-indexed #(when (= task-id (:task-id %2))
                                         %1)
                                     task-statuses))]
      (reset! (:task-status-atom this) (-> task-statuses
                                           (assoc-in [index :status] status)
                                           (assoc-in [index :status-message] status-message)))))

  (update-bulk-update-collection-status
    [this task-id concept-id status status-message]
    (let [coll-statuses (into [] @collection-status-atom)
          index (first (keep-indexed #(when (and (= concept-id (:concept-id %2))
                                                 (= task-id (:task-id %2)))
                                         %1)
                                     coll-statuses))]
      (reset! (:collection-status-atom this) (-> coll-statuses
                                                (assoc-in [index :status] status)
                                                (assoc-in [index :status-message] status-message)))))

  (reset-bulk-update
    [this]
    (reset! task-status-atom [])
    (reset! collection-status-atom []))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    this)
  (stop
    [this system]
    this))

(defn create-in-memory-db
  []
  (->MemoryDB (atom nil) (atom []) (atom []) (atom 0)))
