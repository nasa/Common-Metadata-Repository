(ns cmr.ingest.data.memory-db
  "Stores and retrieves the hashes of the ACLs for a provider."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.ingest.data.bulk-update :as data-bulk-update]
   [cmr.ingest.data.granule-bulk-update :as granule-data-bulk-update]
   [cmr.ingest.data.provider-acl-hash :as acl-hash]))

(defrecord ACLHashMemoryStore

  [acl-hash-data-atom
   task-status-atom
   collection-status-atom
   task-id-atom
   granule-acl-hash-data-atom
   granule-task-status-atom
   granule-status-atom
   granule-task-id-atom]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  acl-hash/AclHashStore

  (save-acl-hash
   [this acl-hash]
   (reset! (:acl-hash-data-atom this) acl-hash))

  (get-acl-hash
   [this]
   (-> this :acl-hash-data-atom deref))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  data-bulk-update/BulkUpdateStore

  (get-provider-bulk-update-status
   [this provider-id]
   (some->> @task-status-atom
            (filter #(= provider-id (:provider-id %)))
            (map #(select-keys % [:created-at :name :task-id :status :status-message :request-json-body]))))

  (get-bulk-update-task-status
   [this task-id provider-id]
   (let [task-status (some->> @task-status-atom
                              (some #(when (and (= provider-id (str (:provider-id %)))
                                                (= task-id (str (:task-id %))))
                                       %)))]
     (select-keys task-status [:created-at :name :task-id :status :status-message :request-json-body])))

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
   (let [task-id (str @task-id-atom)
         name (get (json/parse-string json-body) "name" task-id)]
     ;; provider-id and name together need to be unique.
     (if (some #(and (= provider-id (:provider-id %))(= name (:name %)))
               @task-status-atom)
       (throw (Exception. (str "ORA-00001: unique constraint "
                               "(CMR_INGEST.BULK_UPDATE_TASK_STATUS_UK) "
                               "violated\n")))
       (swap! task-status-atom conj {:created-at (str (time-keeper/now))
                                     :task-id task-id
                                     :name name
                                     :provider-id provider-id
                                     :request-json-body json-body
                                     :status "IN_PROGRESS"}))
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
           failed-collections (filter #(= "FAILED" (:status %)) task-collections)
           skipped-collections (filter #(= "SKIPPED" (:status %)) task-collections)]
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
                                                            (count skipped-collections)
                                                            (count task-collections)))))))))))
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  granule-data-bulk-update/GranBulkUpdateStore

  (get-granule-tasks-by-provider-id
   [this provider-id]
   (some->> @granule-task-status-atom
            (filter #(= provider-id (:provider-id %)))
            (map #(select-keys
                   % [:created-at :name :task-id :status :status-message :request-json-body]))))

  (get-granule-task-by-task-id
   [this task-id]
   (let [task-status (some->> @granule-task-status-atom
                              (some #(when (= task-id (str (:task-id %)))
                                       %)))]
     (select-keys task-status
                  [:created-at :name :provider-id :status :status-message :request-json-body])))

  (get-bulk-update-task-granule-status
   [this task-id]
   (some->> @granule-status-atom
            (into [])
            (filter #(= (str task-id) (str (:task-id %))))
            (map #(select-keys % [:granule-ur :status :status-message]))
            (map util/remove-nil-keys)))

  (get-bulk-update-granule-status
   [this task-id granule-ur]
   (let [gran-status (some->> @granule-status-atom
                              (some #(when (and (= granule-ur (:granule-ur %))
                                                (= (str task-id) (:task-id %)))
                                       %)))]
     (select-keys gran-status [:granule-ur :status :status-message])))

  (create-and-save-bulk-granule-update-status
   [this provider-id user-id request-json-body instructions]
   (swap! granule-task-id-atom inc)
   (let [task-id (str @granule-task-id-atom)
         parsed-json (json/parse-string request-json-body true)
         task-name (get parsed-json :name task-id)
         unique-task-name (format "%s: %s" task-name task-id)]
     ;; provider-id and name together need to be unique.
     (if (some #(and (= provider-id (:provider-id %))(= unique-task-name (:name %)))
               @granule-task-status-atom)
       (throw (Exception. (str "ORA-00001: unique constraint "
                               "(CMR_INGEST.GBUT_PN_I) "
                               "violated\n")))
       (swap! granule-task-status-atom conj {:created-at (str (time-keeper/now))
                                             :task-id task-id
                                             :name unique-task-name
                                             :provider-id provider-id
                                             :request-json-body request-json-body
                                             :status "IN_PROGRESS"
                                             :user-id user-id}))
     (swap! granule-status-atom concat (map (fn [instruction]
                                              {:task-id task-id
                                               :granule-ur (:granule-ur instruction)
                                               :provider-id provider-id
                                               :instruction (json/generate-string instruction)
                                               :status "PENDING"})
                                            instructions))
     task-id))

  (update-bulk-granule-update-task-status
   [this task-id status status-message]
   (let [task-statuses @granule-task-status-atom
         index (first (keep-indexed #(when (= task-id (:task-id %2))
                                       %1)
                                    task-statuses))
         message (if (string/blank? status-message) nil status-message)]
     (swap! (:granule-task-status-atom this) (fn [task-statuses]
                                               (-> task-statuses
                                                   (assoc-in [index :status] status)
                                                   (assoc-in [index :status-message] message))))))


  (update-bulk-update-granule-status
   [this task-id granule-ur status status-message]
   ;; @granule-status-atom is a lazy seq so force into []
   (let [gran-statuses (into [] @granule-status-atom)
         index (first (keep-indexed #(when (and (= granule-ur (:granule-ur %2))
                                                (= task-id (:task-id %2)))
                                       %1)
                                    gran-statuses))
         message (if (string/blank? status-message) nil status-message)]
     (swap! (:granule-status-atom this) (fn [gran-statuses]
                                          (-> (into [] gran-statuses)
                                              (assoc-in [index :status] status)
                                              (assoc-in [index :status-message] message))))
     (let [gran-statuses (into [] @granule-status-atom) ; Need to refresh after change
           task-granules (filter #(= task-id (:task-id %)) gran-statuses)
           pending-granules (filter #(= "PENDING" (:status %)) task-granules)
           failed-granules (filter #(= "FAILED" (:status %)) task-granules)
           skipped-granules (filter #(= "SKIPPED" (:status %)) task-granules)]
       (when-not (seq pending-granules)
         (let [task-statuses @granule-task-status-atom
               index (first (keep-indexed #(when (= task-id (:task-id %2))
                                             %1)
                                          task-statuses))]
           (swap! (:granule-task-status-atom this) (fn [task-statuses]
                                                     (-> task-statuses
                                                         (assoc-in [index :status] "COMPLETE")
                                                         (assoc-in [index :status-message]
                                                                   (granule-data-bulk-update/generate-task-status-message
                                                                    (count failed-granules)
                                                                    (count skipped-granules)
                                                                    (count task-granules)))))))))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (reset-bulk-update
   [this]
   (reset! task-status-atom [])
   (reset! collection-status-atom [])
   (reset! task-id-atom 0))

  (reset-bulk-granule-update
   [this]
   (reset! granule-task-status-atom [])
   (reset! granule-status-atom [])
   (reset! granule-task-id-atom 0))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
   [this system]
   this)
  (stop
   [this system]
   this))

(defn create-db
  []
  (->ACLHashMemoryStore
   ; collection atoms
   (atom nil) (atom []) (atom []) (atom 0)

   ; granule atoms
   (atom nil) (atom []) (atom []) (atom 0)))
