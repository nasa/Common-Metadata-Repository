(ns cmr.access-control.services.event-handler
  "Provides functions for subscribing to and handling events."
  (:require
    [cmr.access-control.config :as config]
    [cmr.access-control.data.access-control-index :as index]
    [cmr.access-control.services.acl-service :as acl-service]
    [cmr.common.concepts :as concepts]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.message-queue.queue.queue-protocol :as queue-protocol]
    [cmr.transmit.config :as transmit-config]
    [cmr.transmit.metadata-db2 :as mdb]
    [cmr.umm-spec.acl-matchers :as acl-matchers]))

(defmulti handle-provider-event
  "Handle the various messages that are posted to the provider queue.
  Dispatches on the message action."
  (fn [context {:keys [action] :as msg}]
    (keyword action)))

(defmethod handle-provider-event :provider-delete
  [context {:keys [provider-id]}]
  ;; The actual :access-group concept records are deleted from code within the metadata db
  ;; app itself. We need to ensure that the groups are unindexed here too, or else they will
  ;; still come up in search results, etc..
  (index/unindex-groups-by-provider context provider-id)
  ;; ACLs are also purged from metadata db in the same way.
  (index/unindex-acls-by-provider context provider-id))

;; Default ignores the provider event
(defmethod handle-provider-event :default
  [_ _])

(defmulti handle-indexing-event
  "Handle the various messages that are posted to the indexing queue. Dispatches on a vector of
  action and concept type keywords like [:concept-delete :access-group]."
  (fn [context {:keys [action concept-id] :as msg}]
    [(keyword action)
     (when concept-id
       (:concept-type (concepts/parse-concept-id concept-id)))]))

;; Default ignores the ingest event. There may be ingest events we don't care about.
(defmethod handle-indexing-event :default
  [_ _])

;;; Updates

(defmethod handle-indexing-event [:concept-update :acl]
  [context {:keys [concept-id revision-id]}]
  (index/index-acl context (mdb/get-concept context concept-id revision-id)))

(defmethod handle-indexing-event [:concept-update :access-group]
  [context {:keys [concept-id revision-id]}]
  ;; NOTE: Even though groups are indexed synchronously in the group service, this will enable retries in
  ;; case of failure.
  (index/index-group context (mdb/get-concept context concept-id revision-id)))

;;; Deletes
(defmethod handle-indexing-event [:concept-delete :access-group]
  [context {:keys [concept-id revision-id]}]
  (doseq [acl-concept (acl-service/get-all-acl-concepts context)
          :let [parsed-acl (acl-service/get-parsed-acl acl-concept)
                group-permissions (:group-permissions parsed-acl)]]
    (if (= concept-id (get-in parsed-acl [:single-instance-identity :target-id]))
      ;; When access control groups are deleted, any SingleInstanceIdentity ACL has the group as
      ;; target_id should be deleted;
      (acl-service/delete-acl (transmit-config/with-echo-system-token context)
                              (:concept-id acl-concept))
      ;; Any ACL that has the group in group permissions should be updated.
      (when (contains? (set (map :group-id group-permissions)) concept-id)
        (acl-service/update-acl (transmit-config/with-echo-system-token context)
                                (:concept-id acl-concept)
                                (assoc parsed-acl :group-permissions
                                       (remove #(= (:group-id %) concept-id) group-permissions))))))
  (index/unindex-group context concept-id revision-id))

(defmethod handle-indexing-event [:concept-delete :acl]
  [context {:keys [concept-id revision-id]}]
  (index/unindex-acl context concept-id revision-id))

(defmethod handle-indexing-event [:concept-delete :collection]
  [context {:keys [concept-id revision-id]}]
  (let [concept-map (mdb/get-concept context concept-id revision-id)
        collection-concept (acl-matchers/add-acl-enforcement-fields-to-concept concept-map)]
    (doseq [acl-concept (acl-service/get-all-acl-concepts context)
            :let [parsed-acl (acl-service/get-parsed-acl acl-concept)
                  catalog-item-id (:catalog-item-identity parsed-acl)
                  concept-ids (get (:collection-identifier catalog-item-id) :concept-ids)]
            :when (and (= (:provider-id collection-concept) (:provider-id catalog-item-id))
                       (some #{concept-id} concept-ids))]
      (if (= 1 (count concept-ids))
        ;; The ACL only references the collection being deleted, and therefore the ACL should be deleted.
        ;; With the addition of concept-ids, this assumes entry-titles and concept-ids are in sync.
        (acl-service/delete-acl (transmit-config/with-echo-system-token context)
                                (:concept-id acl-concept))
        ;; Otherwise the ACL references other collections, and will be updated
        (let [new-acl (-> parsed-acl
                          (assoc-in [:catalog-item-identity :collection-identifier :concept-ids]
                                    (remove #(= % concept-id) concept-ids))
                          (update-in [:catalog-item-identity :collection-identifier]
                                     dissoc :entry-titles))]
          (acl-service/update-acl (transmit-config/with-echo-system-token context)
                                  (:concept-id acl-concept) new-acl))))))


(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/index-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                       (config/provider-queue-name)
                       #(handle-provider-event context %))
      (queue-protocol/subscribe queue-broker
                       (config/index-queue-name)
                       #(handle-indexing-event context %)))))
