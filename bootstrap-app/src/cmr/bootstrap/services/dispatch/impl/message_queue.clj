(ns cmr.bootstrap.services.dispatch.impl.message-queue
  "Functions implementing the dispatch protocol to support bootstrap operations using a message
  queue."
  (:require
   [clj-time.core :as time]
   [cmr.bootstrap.api.messages-bulk-index :as msg]
   [cmr.bootstrap.config :as config]
   [cmr.bootstrap.data.bulk-index :as bulk-index]
   [cmr.bootstrap.data.fingerprint :as fingerprint]
   [cmr.bootstrap.data.message-queue :as message-queue]
   [cmr.bootstrap.embedded-system-helper :as helper]
   [cmr.common.log :refer (info)]
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
  [_this context provider-id start-index]
  (message-queue/publish-bootstrap-provider-event
   context
   (message-queue/bootstrap-provider-event provider-id start-index)))

(defn- index-variables
  "Bulk index all the variables. If a provider is passed, only index the variables
  for that provider."
  ([_this context]
   (info (msg/index-variables-start))
   (doseq [provider (helper/get-providers (:system context))
           :let [provider-id (:provider-id provider)]]
     (message-queue/publish-bootstrap-concepts-event
      context
      (message-queue/bootstrap-variables-event provider-id)))
   (info (msg/index-variables-done)))
  ([_this context provider-id]
   (message-queue/publish-bootstrap-concepts-event
    context
    (message-queue/bootstrap-variables-event provider-id))))

(defn- index-services
  "Bulk index all the services. If a provider is passed, only index the services
  for that provider."
  ([_this context]
   (info (msg/index-services-start))
   (doseq [provider (helper/get-providers (:system context))
           :let [provider-id (:provider-id provider)]]
     (message-queue/publish-bootstrap-concepts-event
      context
      (message-queue/bootstrap-services-event provider-id)))
   (info (msg/index-services-done)))
  ([_this context provider-id]
   (message-queue/publish-bootstrap-concepts-event
    context
    (message-queue/bootstrap-services-event provider-id))))

(defn- index-tools
  "Bulk index all the tools. If a provider is passed, only index the tools
  for that provider."
  ([_this context]
   (info (msg/index-tools-start))
   (doseq [provider (helper/get-providers (:system context))
           :let [provider-id (:provider-id provider)]]
     (message-queue/publish-bootstrap-concepts-event
      context
      (message-queue/bootstrap-tools-event provider-id)))
   (info (msg/index-tools-done)))
  ([_this context provider-id]
   (message-queue/publish-bootstrap-concepts-event
    context
    (message-queue/bootstrap-tools-event provider-id))))

(defn- index-subscriptions
  "Bulk index all the subscriptions. If a provider is passed, only index the subscriptions
  for that provider."
  ([_this context]
   (info (msg/index-subscriptions-start))
   (doseq [provider (helper/get-providers (:system context))
           :let [provider-id (:provider-id provider)]]
     (message-queue/publish-bootstrap-concepts-event
      context
      (message-queue/bootstrap-subscriptions-event provider-id)))
   (info (msg/index-subscriptions-done)))
  ([_this context provider-id]
   (message-queue/publish-bootstrap-concepts-event
    context
    (message-queue/bootstrap-subscriptions-event provider-id))))

(defn- index-generics
  "Bulk index all the generic documents of a particular type. If a provider is passed, only index
   the documents for that provider."
  ([_this context concept-type]
   (info (msg/index-generics-start concept-type))
   (doseq [provider (helper/get-providers (:system context))
           :let [provider-id (:provider-id provider)]]
     (message-queue/publish-bootstrap-concepts-event
      context
      (message-queue/bootstrap-generics-event concept-type provider-id)))
   (info (msg/index-all-concepts-complete concept-type)))
  ([_this context concept-type provider-id]
   (info (msg/index-generics-with-provider-start concept-type provider-id))
   (message-queue/publish-bootstrap-concepts-event
    context
    (message-queue/bootstrap-generics-event concept-type provider-id))
   (info (msg/index-generics-with-provider-done concept-type provider-id))))

(defn- index-data-later-than-date-time
  "Bulk index all the concepts with a revision date later than the given date-time."
  [_this context provider-ids date-time]
  (info (msg/index-data-later-than-date-time-start))
  (let [provider-ids (if (seq provider-ids)
                       provider-ids
                       ;; all providers including CMR provider which is for system concepts
                       (conj (map :provider-id (helper/get-providers (:system context))) "CMR"))]
    (doseq [provider-id provider-ids]
      (message-queue/publish-bootstrap-concepts-event
       context
       (message-queue/bootstrap-provider-event provider-id nil date-time)))
    (info (msg/index-data-later-than-date-time-done))))

(defn- date-time-chunks
  "Returns half-open date-time ranges no larger than chunk-hours."
  [start-date-time end-date-time chunk-hours]
  (let [chunk-period (time/hours chunk-hours)]
    (loop [chunk-start start-date-time
           chunks []]
      (if (time/before? chunk-start end-date-time)
        (let [candidate-end (time/plus chunk-start chunk-period)
              chunk-end (if (time/before? candidate-end end-date-time)
                          candidate-end
                          end-date-time)]
          (recur chunk-end (conj chunks [chunk-start chunk-end])))
        chunks))))

(defn- index-data-between-date-time
  "Bulk index all the concepts with revision dates between the given date-times."
  [_this context provider-ids start-date-time end-date-time]
  (info (format "Publishing bulk index messages between [%s] and [%s]."
                start-date-time end-date-time))
  (let [provider-ids (if (seq provider-ids)
                       provider-ids
                       ;; all providers including CMR provider which is for system concepts
                       (conj (map :provider-id (helper/get-providers (:system context))) "CMR"))
        chunk-hours (max 1 (config/bulk-index-between-date-time-window-hours))
        chunks (date-time-chunks start-date-time end-date-time chunk-hours)]
    (doseq [provider-id provider-ids
            [chunk-start chunk-end] chunks]
      (message-queue/publish-bootstrap-concepts-event
       context
       (message-queue/bootstrap-provider-between-date-time-event
        provider-id chunk-start chunk-end)))
    (info (format "Published %d bulk index messages between [%s] and [%s] using %d hour chunks."
                  (* (count provider-ids) (count chunks))
                  start-date-time
                  end-date-time
                  chunk-hours))))

(defn- fingerprint-variables
  "Update fingerprints of variables. If a provider is passed, only update fingerprints of the
  variables for that provider."
  ([_this context]
   (info "Publishing fingerprint events for all variables.")
   (doseq [provider (helper/get-providers (:system context))
           :let [provider-id (:provider-id provider)]]
     (message-queue/publish-bootstrap-concepts-event
      context
      (message-queue/fingerprint-variables-event provider-id)))
   (info "Publishing fingerprint events for all variables completed."))
  ([_this context provider-id]
   (message-queue/publish-bootstrap-concepts-event
    context
    (message-queue/fingerprint-variables-event provider-id))))

(defrecord MessageQueueDispatcher
           [])

(def dispatch-behavior
  "Map of protocol definitions to the implementations of that protocol for the message queue
  dispatcher."
  {:migrate-provider (partial not-implemented :migrate-provider)
   :migrate-collection (partial not-implemented :migrate-collection)
   :index-provider index-provider
   :index-variables index-variables
   :index-services index-services
   :index-tools index-tools
   :index-subscriptions index-subscriptions
   :index-generics index-generics
   :index-data-later-than-date-time index-data-later-than-date-time
   :index-data-between-date-time index-data-between-date-time
   :index-collection (partial not-implemented :index-collection)
   :index-system-concepts (partial not-implemented :index-system-concepts)
   :index-concepts-by-id (partial not-implemented :index-concepts-by-id)
   :delete-concepts-from-index-by-id (partial not-implemented :delete-concepts-from-index-by-id)
   :bootstrap-virtual-products (partial not-implemented :bootstrap-virtual-products)
   :fingerprint-variables fingerprint-variables})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message handling

(defmulti handle-bootstrap-event
  "Handle the actions that can be requested for a provider via the bootstrap-queue."
  (fn [_context msg]
    (keyword (:action msg))))

(defmethod handle-bootstrap-event :index-provider
  [context msg]
  (if-let [start-index (:start-index msg)]
    (bulk-index/index-provider (:system context) (:provider-id msg) start-index msg/bulk-index-prefix-queue)
    (bulk-index/index-provider-data-later-than-date-time
     (:system context) (:provider-id msg) (:date-time msg))))

(defmethod handle-bootstrap-event :index-provider-between-date-time
  [context msg]
  (bulk-index/index-provider-data-between-date-time
   (:system context)
   (:provider-id msg)
   (:start-date-time msg)
   (:end-date-time msg)))

(defmethod handle-bootstrap-event :index-variables
  [context msg]
  (if-let [provider-id (:provider-id msg)]
    (bulk-index/index-provider-concepts (:system context) :variable provider-id)
    (bulk-index/index-all-concepts (:system context) :variable)))

(defmethod handle-bootstrap-event :index-services
  [context msg]
  (if-let [provider-id (:provider-id msg)]
    (bulk-index/index-provider-concepts (:system context) :service provider-id)
    (bulk-index/index-all-concepts (:system context) :service)))

(defmethod handle-bootstrap-event :index-tools
  [context msg]
  (if-let [provider-id (:provider-id msg)]
    (bulk-index/index-provider-concepts (:system context) :tool provider-id)
    (bulk-index/index-all-concepts (:system context) :tool)))

(defmethod handle-bootstrap-event :index-subscriptions
  [context msg]
  (if-let [provider-id (:provider-id msg)]
    (bulk-index/index-provider-concepts (:system context) :subscription provider-id)
    (bulk-index/index-all-concepts (:system context) :subscription)))

(defmethod handle-bootstrap-event :index-generics
  [context msg]
  (if-let [provider-id (:provider-id msg)]
    (bulk-index/index-provider-concepts (:system context) (keyword (:concept-type msg)) provider-id)
    (bulk-index/index-all-concepts (:system context) (keyword (:concept-type msg)))))

(defmethod handle-bootstrap-event :fingerprint-variables
  [context msg]
  (fingerprint/fingerprint-variables (:system context) (:provider-id msg)))

(defn subscribe-to-events
  "Subscribe to event messages on bootstrap queues."
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/bootstrap-queue-listener-count)]
      (info (msg/subscribe-to-events-start n))
      (queue-protocol/subscribe queue-broker
                                (config/bootstrap-queue-name)
                                #(handle-bootstrap-event context %))
      (info (msg/subscribe-to-events-done n)))))
