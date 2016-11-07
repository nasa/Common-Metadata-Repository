(ns cmr.access-control.services.event-handler
  "Provides functions for subscribing to and handling events."
  (:require
    [cmr.access-control.config :as config]
    [cmr.access-control.data.access-control-index :as index]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.message-queue.services.queue :as queue]
    [cmr.transmit.metadata-db2 :as mdb]
    [cmr.umm.acl-matchers :as acl-matchers]
    [cmr.access-control.services.acl-service :as acl-service]
    [cmr.transmit.config :as transmit-config]
    [cmr.common.concepts :as concepts]))

(defmulti handle-event
  "Handle the various messages that are posted to the indexing queue. Dispatches on a vector of
  action and concept type keywords like [:concept-delete :access-group]."
  (fn [context {:keys [action concept-id] :as msg}]
    [(keyword action)
     (when concept-id
       (:concept-type (concepts/parse-concept-id concept-id)))]))

;; Default ignores the ingest event. There may be ingest events we don't care about.

(defmethod handle-event :default
  [_ _])

;;; Updates

(defmethod handle-event [:concept-update :acl]
  [context {:keys [concept-id revision-id]}]
  (index/index-acl context (mdb/get-concept context concept-id revision-id)))

(defmethod handle-event [:concept-update :access-group]
  [context {:keys [concept-id revision-id]}]
  ;; NOTE: Even though groups are indexed synchronously in the group service, this will enable retries in
  ;; case of failure.
  (index/index-group context (mdb/get-concept context concept-id revision-id)))

;;; Deletes

(defmethod handle-event [:concept-delete :access-group]
  [context {:keys [concept-id]}]
  ;; When access control groups are deleted, all ACLs referencing the group should be cleaned up.
  (doseq [acl-concept (acl-service/get-all-acl-concepts context)
          :let [parsed-acl (acl-service/get-parsed-acl acl-concept)]
          :when (= concept-id (get-in parsed-acl [:single-instance-identity :target-id]))]
    (acl-service/delete-acl context (:concept-id acl-concept)))
  (index/unindex-group context concept-id))

(defmethod handle-event [:concept-delete :acl]
  [context {:keys [concept-id]}]
  (index/unindex-acl context concept-id))

(defmethod handle-event [:provider-delete nil]
  [context {:keys [provider-id]}]
  ;; The actual :access-group concept records are deleted from code within the metadata db
  ;; app itself. We need to ensure that the groups are unindexed here too, or else they will
  ;; still come up in search results, etc..
  (index/unindex-groups-by-provider context provider-id)
  ;; ACLs are also purged from metadata db in the same way.
  (index/unindex-acls-by-provider context provider-id))

(defmethod handle-event [:concept-delete :collection]
  [context {:keys [concept-id revision-id]}]
  (let [concept-map (mdb/get-concept context concept-id revision-id)
        collection-concept (acl-matchers/add-acl-enforcement-fields-to-concept concept-map)
        entry-title (:entry-title collection-concept)]
    (doseq [acl-concept (acl-service/get-all-acl-concepts context)
            :let [parsed-acl (acl-service/get-parsed-acl acl-concept)
                  catalog-item-id (:catalog-item-identity parsed-acl)
                  acl-entry-titles (:entry-titles (:collection-identifier catalog-item-id))]
            :when (and (= (:provider-id collection-concept) (:provider-id catalog-item-id))
                       (some #{entry-title} acl-entry-titles))]
      (if (= 1 (count acl-entry-titles))
        ;; the ACL only references the collection being deleted, and therefore the ACL should be deleted
        (acl-service/delete-acl context (:concept-id acl-concept))
        ;; otherwise the ACL references other collections, and will be updated
        (let [new-acl (update-in parsed-acl
                                 [:catalog-item-identity :collection-identifier :entry-titles]
                                 #(remove #{entry-title} %))]
          (acl-service/update-acl (transmit-config/with-echo-system-token context) (:concept-id acl-concept) new-acl))))))

(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/index-queue-listener-count)]
      (queue/subscribe queue-broker
                       (config/index-queue-name)
                       #(handle-event context %)))))
