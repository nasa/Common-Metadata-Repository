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
  (let [event (message-queue/bootstrap-provider-event provider-id start-index)]
    (message-queue/publish-bootstrap-event context event)))

(defrecord MessageQueueDispatcher
  [])

(def dispatch-behavior
  "Map of protocol definitions to the implementations of that protocol for the message queue
  dispatcher."
  {:migrate-provider (partial not-implemented :migrate-provider)
   :migrate-collection (partial not-implemented :migrate-collection)
   :index-provider index-provider
   :index-data-later-than-date-time (partial not-implemented :index-data-later-than-date-time)
   :index-collection (partial not-implemented :index-collection)
   :index-system-concepts (partial not-implemented :index-system-concepts)
   :index-concepts-by-id (partial not-implemented :index-concepts-by-id)
   :delete-concepts-from-index-by-id (partial not-implemented :delete-concepts-from-index-by-id)
   :bootstrap-virtual-products (partial not-implemented :bootstrap-virtual-products)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message handling

(defmulti handle-bootstrap-provider-event
  "Handle the actions that can be requested for a provider via the bootstrap-provider-queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-bootstrap-provider-event :index-provider
  [context msg]
  (bulk-index/index-provider (:system context) (:provider-id msg) (:start-index msg)))

(defn subscribe-to-events
  "Subscribe to event messages on bootstrap queues."
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/bootstrap-provider-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/bootstrap-provider-queue-name)
                                #(handle-bootstrap-provider-event context %)))))
