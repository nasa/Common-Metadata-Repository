(ns cmr.bootstrap.api.rebalancing
  "Defines the rebalancing functions for the bootstrap API."
  (:require
   [cmr.bootstrap.api.messages :as msg]
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.services.bootstrap-service :as service]))

(defn start-collection
  "Kicks off rebalancing the granules in the collection into their own index."
  [context concept-id params]
  (let [dispatcher (api-util/get-dispatcher context params :index-collection)
        target (get params :target "separate-index")]
    (service/start-rebalance-collection context dispatcher concept-id target)
    {:status 200
     :body {:message (msg/rebalancing-started concept-id)}}))

(defn get-status
  "Gets the status of rebalancing a collection."
  [context concept-id]
  {:status 200
   :body (service/rebalance-status context concept-id)})

(defn finalize-collection
  "Completes rebalancing the granules in the collection"
  [context concept-id]
  (service/finalize-rebalance-collection context concept-id)
  {:status 200
   :body {:message (msg/rebalancing-completed concept-id)}})
