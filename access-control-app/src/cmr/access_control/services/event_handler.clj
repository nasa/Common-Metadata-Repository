(ns cmr.access-control.services.event-handler
  "Provides functions for subscribing to and handling events."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.access-control.config :as config]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.access-control.data.access-control-index :as index]))

(defmulti handle-event
  "Handle the various actions that can be requested via the indexing queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-event :default
  [_ _])
  ;; Default ignores the ingest event. There may be ingest events we don't care about.

(defmethod handle-event :concept-update
  [context {:keys [concept-id revision-id]}]
  (index/index-concept context (mdb/get-concept context concept-id revision-id)))

(defmethod handle-event :concept-delete
  [context {:keys [concept-id revision-id]}]
  (index/delete-concept context (mdb/get-concept context concept-id revision-id)))

(defmethod handle-event :provider-delete
  [context {:keys [provider-id]}]
  ;; The actual :access-group concept records are deleted from code within the metadata db
  ;; app itself. We need to ensure that the groups are unindexed here too, or else they will
  ;; still come up in search results, etc..
  (index/delete-provider-groups context provider-id)
  ;; ACLs are also purged from metadata db in the same way.
  (index/delete-provider-acls context provider-id))

(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/index-queue-listener-count)]
      (queue/subscribe queue-broker
                       (config/index-queue-name)
                       #(handle-event context %)))))
