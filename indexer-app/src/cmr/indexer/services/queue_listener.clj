(ns cmr.indexer.services.queue-listener
  "Queue subscriber methods"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.indexer.config :as config]
            [cmr.indexer.services.index-service :as indexer]
            [cmr.message-queue.services.queue :as queue]))

(defmulti handle-index-action
  "Handle the various actions that can be requested via the indexing queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-index-action :index-concept
  [context msg]
  (let [{:keys [concept-id revision-id]} msg]
    (try
      (indexer/index-concept context concept-id revision-id true)
      {:status :ok}
      (catch Exception e
        {:status :retry :message (.getMessage e)}))))

(defn start-queue-message-handler
  "Subscribe to messages on the indexing queue."
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])
        queue-name (config/index-queue-name)]

    (queue/subscribe queue-broker queue-name #(handle-index-action context %) {})))
