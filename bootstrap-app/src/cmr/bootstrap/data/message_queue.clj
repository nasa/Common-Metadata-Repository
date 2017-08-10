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
  [provider-id start-index]
  {:action :index-provider
   :provider-id provider-id
   :start-index start-index})
