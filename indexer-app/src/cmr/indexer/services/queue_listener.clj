(ns cmr.indexer.services.queue-listener
  "Provides functions related to subscribing to the indexing queue. Creates
  separate subscriber threads to listen on the indexing queue for index requests
  with start-queue-message-handler and provides a multi-method, handle-index-action,
  to actually process the messages."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.indexer.config :as config]
            [cmr.indexer.services.index-service :as indexer]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.log :refer (debug info warn error)]))

(defmulti handle-index-action
  "Handle the various actions that can be requested via the indexing queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-index-action :index-concept
  [context msg]
  (let [{:keys [concept-id revision-id]} msg]
    (try
      (indexer/index-concept context concept-id revision-id true)
      {:status :success}
      (catch Exception e
        (error e (.getMessage e))
        {:status :retry :message (.getMessage e)}))))

(defmethod handle-index-action :delete-concept
  [context msg]
  (let [{:keys [concept-id revision-id]} msg]
    (try
      (indexer/delete-concept context concept-id revision-id true)
      {:status :success}
      (catch Exception e
        (error e (.getMessage e))
        {:status :retry :message (.getMessage e)}))))

(defn start-queue-message-handler
  "Subscribe to messages on the indexing queue."
  [context message-handler]
  (let [queue-broker (get-in context [:system :queue-broker])
        queue-name (config/index-queue-name)]

    (queue/subscribe queue-broker queue-name #(message-handler context %) {})))
