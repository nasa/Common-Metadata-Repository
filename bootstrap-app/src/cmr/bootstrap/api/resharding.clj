(ns cmr.bootstrap.api.resharding
  "Defines the resharding functions for the bootstrap API."
  (:require
   [cmr.bootstrap.api.messages :as msg]
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.services.bootstrap-service :as service]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.es-index-helper :as index-helper]))

(defn- validate-num-shards
  "Validate the the number of shards is a positive integer"
  [num-shards-str]
  (println "VALIDATING NUM-SHARDS=================")
  (let [num-shards (parse-long num-shards-str)]
    (when-not (and num-shards (> num-shards 0))
      (errors/throw-service-errors
       :bad-request
       [(format "Invalid num_shards [%s]. Only integers greater than zero are allowed."
                num-shards-str)]))))

(defn start
  "Kicks off resharding of an index."
  [context index params]
  (let [num-shards-str (:num_shards params)]
    (validate-num-shards num-shards-str)
    (service/start-reshard-index context index (parse-long num-shards-str))
    {:status 200
     :body {:message (msg/resharding-started index)}}))

;; (defn get-status
;;   "Gets the status of rebalancing a collection."
;;   [context concept-id]
;;   {:status 200
;;    :body (service/rebalance-status context concept-id)})

;; (defn finalize
;;   "Completes rebalancing the granules in the collection"
;;   [context concept-id]
;;   (service/finalize-rebalance-collection context concept-id)
;;   {:status 200
;;    :body {:message (msg/rebalancing-completed concept-id)}})