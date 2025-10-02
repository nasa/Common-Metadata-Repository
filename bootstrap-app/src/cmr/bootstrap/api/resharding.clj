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
  (let [dispatcher (api-util/get-dispatcher context params :migrate-index)
        num-shards-str (:num_shards params)]
    (validate-num-shards num-shards-str)
    (service/start-reshard-index context dispatcher index (parse-long num-shards-str))
    {:status 200
     :body {:message (msg/resharding-started index)}}))

;; TODO add this in CMR-10771
;; (defn get-status
;;   "Gets the status of resharding an index."
;;   [context index]
;;   {:status 200
;;    :body (service/resharding-status context index})

;; TODO add this in CMR-10770
;; (defn finalize
;;   "Completes resharding the index"
;;   [context index]
;;   (service/finalize-resharding context index)
;;   {:status 200
;;    :body {:message (msg/resharding-completed index)}})