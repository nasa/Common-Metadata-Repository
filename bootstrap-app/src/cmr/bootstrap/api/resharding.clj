(ns cmr.bootstrap.api.resharding
  "Defines the resharding functions for the bootstrap API."
  (:require
   [clojure.string :as string]
   [cmr.bootstrap.api.messages :as msg]
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.services.bootstrap-service :as service]
   [cmr.common.services.errors :as errors]))

(defn- validate-num-shards
  "Validates that the number of shards is a positive integer."
  [num-shards-str]
  (when-not (seq num-shards-str)
    (errors/throw-service-errors
     :bad-request
     ["num_shards is a required parameter."]))

  (let [num-shards (parse-long num-shards-str)]
    (when-not (and num-shards (pos? num-shards))
      (errors/throw-service-errors
       :bad-request
       [(format "Invalid num_shards [%s]. Only integers greater than zero are allowed."
                num-shards-str)]))))

(defn- validate-es-cluster-name-not-blank
  [es-cluster-name]
  (when (string/blank? es-cluster-name)
    (errors/throw-service-error :bad-request "Empty elastic cluster name is not allowed.")))

(defn start
  "Kicks off resharding of an index."
  [context index params]
  (let [es-cluster-name (:elastic_name params)
        _ (validate-es-cluster-name-not-blank es-cluster-name)
        num-shards-str (:num_shards params)
        _ (validate-num-shards num-shards-str)
        dispatcher (api-util/get-dispatcher context params :migrate-index)]

    (service/start-reshard-index context dispatcher index (parse-long num-shards-str) es-cluster-name)
    {:status 200
     :body {:message (msg/resharding-started index)}}))

(defn get-status
  "Gets the status of resharding an index."
  [context index params]
  (let [es-cluster-name (:elastic_name params)]
    (validate-es-cluster-name-not-blank es-cluster-name)
    (service/reshard-status context index es-cluster-name)))

(defn finalize
  "Completes resharding the index"
  [context index params]
  (let [es-cluster-name (:elastic_name params)]
    (validate-es-cluster-name-not-blank es-cluster-name)
    (service/finalize-reshard-index context index es-cluster-name)
    {:status 200
     :body {:message (msg/resharding-completed index)}}))
