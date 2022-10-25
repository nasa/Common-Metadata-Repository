(ns cmr.bootstrap.data.message-queue
  "Broadcast of bootstrap actions via the message queue"
  (:require
   [cmr.bootstrap.config :as config]
   [cmr.common.log :refer [info]]
   [cmr.message-queue.services.queue :as queue]))

(defn publish-bootstrap-provider-event
  "Put a bootstrap provider event on the message queue."
  [context msg]
  (let [queue-broker (get-in context [:system :queue-broker])
        exchange-name (config/bootstrap-exchange-name)]
    (info "Publishing bootstrap message: " msg)
    (queue/publish-message queue-broker exchange-name msg)))

(defn bootstrap-provider-event
  "Creates an event indicating to bootstrap a provider."
  ([provider-id start-index]
   {:action :index-provider
    :provider-id provider-id
    :start-index start-index})
  ([provider-id _ date-time]
   {:action :index-provider
    :provider-id provider-id
    :date-time date-time}))

(defn publish-bootstrap-concepts-event
  "Put a bootstrap variables event on the message queue."
  [context msg]
  (let [queue-broker (get-in context [:system :queue-broker])
        exchange-name (config/bootstrap-exchange-name)]
    (info "Publishing bootstrap message: " msg)
    (queue/publish-message queue-broker exchange-name msg)))

(defn bootstrap-variables-event
  "Creates an event indicating to bootstrap a variable."
  ([]
   {:action :index-variables})
  ([provider-id]
   (assoc (bootstrap-variables-event) :provider-id provider-id)))

(defn bootstrap-services-event
  "Creates an event indicating to bootstrap a service."
  ([]
   {:action :index-services})
  ([provider-id]
   (assoc (bootstrap-services-event) :provider-id provider-id)))

(defn bootstrap-tools-event
  "Creates an event indicating to bootstrap a tool."
  ([]
   {:action :index-tools})
  ([provider-id]
   (assoc (bootstrap-tools-event) :provider-id provider-id)))

(defn bootstrap-subscriptions-event
  "Creates an event indicating to bootstrap a subscription."
  ([]
   {:action :index-subscriptions})
  ([provider-id]
   (assoc (bootstrap-subscriptions-event) :provider-id provider-id)))

(defn bootstrap-generics-event
  "Creates an event indicating to bootstrap a generic document of type concept-type."
  ([concept-type]
   {:action :index-generics
    :concept-type concept-type})
  ([concept-type provider-id]
   (assoc (bootstrap-generics-event concept-type) :provider-id provider-id)))

(defn fingerprint-variables-event
  "Creates an event indicating to update fingerprints of variables."
  [provider-id]
  {:action :fingerprint-variables
   :provider-id provider-id})
