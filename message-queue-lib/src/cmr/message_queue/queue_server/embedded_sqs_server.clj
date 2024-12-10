(ns cmr.message-queue.queue-server.embedded-sqs-server
  "Used to run a sqs server inside a docker container."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug error]]
   [cmr.message-queue.config :as config])
  (:import
   (org.testcontainers.containers FixedHostPortGenericContainer)))

(def ELASTIC_MQ_UI_PORT 9325)

(def ^:private elasticmq-image
  "Official latest elasticmq image."
  "softwaremill/elasticmq-native:1.6.8")

(defn- build-sqs-server
  "Setup elasticmq docker image and expose the service port and the UI port."
  [http-port1 http-port2]
  (-> (FixedHostPortGenericContainer. elasticmq-image)
      (.withFixedExposedPort (Integer. http-port1) (Integer. http-port1))
      (.withFixedExposedPort (Integer. http-port2) (Integer. http-port2))))

(defrecord SQSServer [queue-port ui-port opts]

  lifecycle/Lifecycle

  (start
    [this _system]
    (debug "Starting ElasticMQ server on ports" queue-port ui-port)
    (let [^FixedHostPortGenericContainer sqs-server (build-sqs-server queue-port ui-port)]
      (try
        (.start sqs-server)
        (assoc this :sqs-server sqs-server)
        (catch Exception e
          (error "ElasticMQ failed to start.")
          (throw (ex-info "ElasticMQ failure" {:exception e}))))))

  (stop
    [this _system]
    (when-let [sqs-server (:sqs-server this)]
      (.stop sqs-server))))

(defn create-sqs-server
  ([]
   (create-sqs-server (config/sqs-server-port) ELASTIC_MQ_UI_PORT))
  ([mq-port ui-port]
   (create-sqs-server mq-port ui-port {}))
  ([mq-port ui-port opts]
   (->SQSServer mq-port ui-port opts)))

(comment
  (def server (create-sqs-server))
  (def started-server (lifecycle/start server nil))
  (def stopped-server (lifecycle/stop started-server nil)))
