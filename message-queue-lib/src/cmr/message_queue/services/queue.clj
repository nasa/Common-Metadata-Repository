(ns cmr.message-queue.services.queue
  "Declares a protocol for creating and interacting with queues and a record that defines
  a queue listener"
  (:require
   [clojail.core :as timeout]
   [cmr.common.log :as log :refer [debug error info warn]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.message-queue.config :as config]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]))

(defn retry-limit-met?
  "Determine whether or not the given message has met (or exceeded)
  the number of allowed retries"
  [msg retry-limit]
  (when-let [retry-count (:retry-count msg)]
    (>= retry-count retry-limit)))

(defn- try-to-publish
  "Attempts to publish messages to the given exchange.

  When the messaging service is down or unreachable, calls to publish will throw an exception. Rather
  than raise an error to the caller immediately, the publication will be retried indefinitely.
  By retrying, routine maintenance such as restarting the RabbitMQ server will not result in any
  ingest errors returned to the provider.

  Returns true if the message was successfully enqueued and false otherwise."
  [queue-broker exchange-name msg]
  (when-not (try
              (queue-protocol/publish-to-exchange queue-broker exchange-name msg)
              (catch Exception e
                (error e)
                false))
    (warn "Attempt to queue messaged failed. Retrying: " msg)
    ;; May be a systemic problem causing the timeout. Reinitialize the queue-broker connection.
    (queue-protocol/reconnect queue-broker)
    (Thread/sleep (config/messaging-retry-delay))
    (recur queue-broker exchange-name msg)))

(defn-timed publish-message
  "Publishes a message to an exchange Throws a service unavailable error if the message
  fails to be put on the queue.

  Requests to publish a message are wrapped in a timeout to handle error cases with the Rabbit MQ
  server. Otherwise failures to publish will be retried indefinitely."
  [queue-broker exchange-name msg]
  (debug (format "Publishing message: %s exchange: [%s]" msg exchange-name))
  (let [start-time (System/currentTimeMillis)]
    (try
      (timeout/thunk-timeout #(try-to-publish queue-broker exchange-name msg)
                             (config/publish-queue-timeout-ms))
      (catch java.util.concurrent.TimeoutException e
        (errors/throw-service-error
          :service-unavailable
          (str "Request timed out when attempting to publish message: " msg)
          e)))))
