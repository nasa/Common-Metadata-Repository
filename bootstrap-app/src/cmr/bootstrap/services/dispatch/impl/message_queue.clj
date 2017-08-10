(ns cmr.bootstrap.services.dispatch.impl.message-queue
  "Functions implementing the dispatch protocol to support synchronous calls."
  (:require
   [cmr.bootstrap.config :as config]
   [cmr.bootstrap.data.bulk-index :as bulk-index]
   [cmr.bootstrap.data.message-queue :as message-queue]
   [cmr.common.services.errors :as errors]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]))

(defn- not-implemented
  "Throws an exception indicating that the specified function is not implemented for
  the message queue dispatcher."
  [action & _]
  (errors/internal-error!
   (format "Message Queue Dispatcher does not support %s action." (name action))))

(defn- index-provider
  "Bulk index all the collections and granules for a provider."
  [this context provider-id start-index]
  (message-queue/publish-bootstrap-provider-event
    context
    (message-queue/bootstrap-provider-event provider-id start-index)))

(defn- index-variables
  "Bulk index all the variables. If a provider is passed, only index the variables
  for that provider."
  ([this context]
   (message-queue/publish-bootstrap-variables-event
     context
     (message-queue/bootstrap-variables-event)))
  ([this context provider-id]
   (message-queue/publish-bootstrap-variables-event
     context
     (message-queue/bootstrap-variables-event provider-id))))

(defrecord MessageQueueDispatcher
  [])

(def dispatch-behavior
  "Map of protocol definitions to the implementations of that protocol for the message queue
  dispatcher."
  {:migrate-provider (partial not-implemented :migrate-provider)
   :migrate-collection (partial not-implemented :migrate-collection)
   :index-provider index-provider
   :index-variables index-variables
   :index-data-later-than-date-time (partial not-implemented :index-data-later-than-date-time)
   :index-collection (partial not-implemented :index-collection)
   :index-system-concepts (partial not-implemented :index-system-concepts)
   :index-concepts-by-id (partial not-implemented :index-concepts-by-id)
   :delete-concepts-from-index-by-id (partial not-implemented :delete-concepts-from-index-by-id)
   :bootstrap-virtual-products (partial not-implemented :bootstrap-virtual-products)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message handling

(defmulti handle-bootstrap-event
  "Handle the actions that can be requested for a provider via the bootstrap-queue."
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-bootstrap-event :index-provider
  [context msg]
  (bulk-index/index-provider (:system context) (:provider-id msg) (:start-index msg)))

(defmethod handle-bootstrap-event :index-variables
  [context msg]
  (if-let [provider-id (:provider-id msg)]
    (bulk-index/index-variables (:system context) provider-id)
    (bulk-index/index-all-variables (:system context))))

(defn subscribe-to-events
  "Subscribe to event messages on bootstrap queues."
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/bootstrap-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/bootstrap-queue-name)
                                #(handle-bootstrap-event context %)))))
