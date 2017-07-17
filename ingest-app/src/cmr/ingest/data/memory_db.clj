(ns cmr.ingest.data.memory-db
  "Stores and retrieves the hashes of the ACLs for a provider."
  (:require
   [clojure.edn :as edn]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.ingest.data.bulk-update :as bulk-update]
   [cmr.ingest.data.bulk-update :as data-bulk-update]
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
             (map #(select-keys % [:task-id :status :status-message :request-json-body]))))

  (get-bulk-update-task-status
    [this task-id]
    (let [task-status (some->> @task-status-atom
                               (some #(when (= task-id (str (:task-id %)))
                                            %)))]
      (select-keys task-status [:task-id :status :status-message :request-json-body])))

  (get-bulk-update-task-collection-status
    [this task-id]
    (some->> @collection-status-atom
             (into [])
             (filter #(= (str task-id) (str (:task-id %))))
             (map #(select-keys % [:concept-id :status :status-message]))))

  (get-bulk-update-collection-status
    [this task-id concept-id]
    (let [coll-status (some->> @collection-status-atom
                               (some #(when (and (= concept-id (:concept-id %))
                                                 (= (str task-id) (:task-id %)))
                                            %)))]
      (select-keys coll-status [:concept-id :status :status-message])))

  (create-and-save-bulk-update-status
    [this provider-id json-body concept-ids]
    (swap! task-id-atom inc)
    (let [task-id (str @task-id-atom)]
     (swap! task-status-atom conj {:task-id task-id
                                   :provider-id provider-id
                                   :request-json-body json-body
                                   :status "IN_PROGRESS"})
     (swap! collection-status-atom concat (map (fn [c]
                                                {:task-id task-id
                                                 :concept-id c
                                                 :status "PENDING"})
                                               concept-ids))
     task-id))

  (update-bulk-update-task-status
    [this task-id status status-message]
    (let [task-statuses @task-status-atom
          index (first (keep-indexed #(when (= task-id (:task-id %2))
                                         %1)
                                     task-statuses))]
      (swap! (:task-status-atom this) (fn [task-statuses]
                                       (-> task-statuses
                                           (assoc-in [index :status] status)
                                           (assoc-in [index :status-message] status-message))))))


  (update-bulk-update-collection-status
    [this task-id concept-id status status-message]
    ;; @collection-status-atom is a lazy seq so force into []
    (let [coll-statuses (into [] @collection-status-atom)
          index (first (keep-indexed #(when (and (= concept-id (:concept-id %2))
                                                 (= task-id (:task-id %2)))
                                         %1)
                                     coll-statuses))]
      (swap! (:collection-status-atom this) (fn [coll-statuses]
                                              (-> (into [] coll-statuses)
                                                  (assoc-in [index :status] status)
                                                  (assoc-in [index :status-message] status-message))))
      (let [coll-statuses (into [] @collection-status-atom) ; Need to refresh after change
            task-collections (filter #(= task-id (:task-id %)) coll-statuses)
            pending-collections (filter #(= "PENDING" (:status %)) task-collections)
            failed-collections (filter #(= "FAILED" (:status %)) task-collections)]
        (when-not (seq pending-collections)
          (let [task-statuses @task-status-atom
                index (first (keep-indexed #(when (= task-id (:task-id %2))
                                               %1)
                                           task-statuses))]
            (swap! (:task-status-atom this) (fn [task-statuses]
                                             (-> task-statuses
                                                 (assoc-in [index :status] "COMPLETE")
                                                 (assoc-in [index :status-message]
                                                   (data-bulk-update/generate-task-status-message
                                                     (count failed-collections)
                                                     (count task-collections)))))))))))

  (reset-bulk-update
    [this]
    (reset! task-status-atom [])
    (reset! collection-status-atom [])
    (reset! task-id-atom 0))

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
