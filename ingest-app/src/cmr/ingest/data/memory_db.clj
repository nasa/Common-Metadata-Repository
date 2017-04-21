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

  (get-bulk-update-status
    [this provider-id]
    (some->> (-> this :task-status-atom deref)
             (filter #(= provider-id (:provider-id %)))
             (map #(select-keys % [:task-id :status :status-message]))))

  (get-bulk-update-task-status
    [this provider-id task-id]
    (let [task-status (some->> (-> this :task-status-atom deref)
                               (some #(when (and (= provider-id (:provider-id %))
                                                 (= task-id (:task-id %)))
                                            %)))]
      (select-keys task-status [:task-id :status :status-message])))

  (get-bulk-update-task-collection-status
    [this task-id]
    (some->> (-> this :collection-status-atom deref)
             (filter #(= task-id (:task-id %)))
             (map #(select-keys % [:concept-id :status :status-message]))))

  (get-bulk-update-collection-status
    [this task-id concept-id]
    (let [coll-status (some->> (-> this :collection-status-atom deref)
                               (some #(when (and (= concept-id (:concept-id %))
                                                 (= task-id (:task-id %)))
                                            %)))]
      (select-keys coll-status [:concept-id :status :status-message])))

  (create-and-save-bulk-update-status
    [this provider-id json-body concept-ids]
    (swap! (:task-id-atom this) inc)
    (let [task-id (-> this :task-id-atom deref)
          task-statuses (-> this :task-status-atom deref)
          collection-statuses (-> this :collection-status-atom deref)]
     (reset! (:task-status-atom this) (conj task-statuses
                                            {:task-id task-id
                                             :provider-id provider-id
                                             :request-json-body json-body
                                             :status "IN_PROGRESS"}))
     (reset! (:collection-status-atom this) (concat collection-statuses
                                                    (map (fn [c]
                                                          {:task-id task-id
                                                           :concept-id c
                                                           :status "PENDING"})
                                                         concept-ids)))))

  (update-bulk-update-task-status
    [this provider-id task-id status status-message]
    (let [task-statuses (-> this :task-status-atom deref)
          index (first (keep-indexed #(when (and (= provider-id (:provider-id %2))
                                                 (= task-id (:task-id %2)))
                                         %1)
                                     task-statuses))]
      (reset! (:task-status-atom this) (-> task-statuses
                                           (assoc-in [index :status] status)
                                           (assoc-in [index :status-message] status-message)))))

  (update-bulk-update-collection-status
    [this task-id concept-id status status-message]
    (let [coll-statuses (into [] (-> this :collection-status-atom deref))
          index (first (keep-indexed #(when (and (= concept-id (:concept-id %2))
                                                 (= task-id (:task-id %2)))
                                         %1)
                                     coll-statuses))]
      (reset! (:collection-status-atom this) (-> coll-statuses
                                                (assoc-in [index :status] status)
                                                (assoc-in [index :status-message] status-message)))))

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
