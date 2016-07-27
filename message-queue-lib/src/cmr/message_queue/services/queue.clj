(ns cmr.message-queue.services.queue
  "Declares a protocol for creating and interacting with queues and a record that defines
  a queue listener"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as util :refer [defn-timed]]
            [cmr.message-queue.config :as config]
            [clojail.core :as timeout]))

(defn retry-limit-met?
  "Determine whether or not the given message has met (or exceeded)
  the number of allowed retries"
  [msg retry-limit]
  (when-let [retry-count (:retry-count msg)]
    (>= retry-count retry-limit)))

(defprotocol Queue
  "Functions for working with a message queue"

  (publish-to-queue
    [this queue-name msg]
    "Publishes a message on the queue with the given queue name. Returns true if the message was
    successfully enqueued. Otherwise returns false.")

  (get-queues-bound-to-exchange
    [this exchange-name]
    "Returns a sequence of queue names that are bound to the given exchange.")

  (publish-to-exchange
    [this exchange-name msg]
    "Publishes a message on the exchange with the given exchange name. Returns true if the message was
    successfully enqueued. Otherwise returns false.")

  (subscribe
    [this queue-name handler-fn]
    "Subscribes to the given queue using the given handler function.

    'handler-fn' is a function that takes a single parameter (the message) and attempts to
    process it. If the function throws an exception the delivery will be retried.")

  (reset
    [this]
    "Reset the broker, deleting any queues and any queued messages")

  (health
    [this]
    "Checks to see if the queue is up and functioning properly. Returns a map with the
    following keys/values:
    :ok? - set to true if the queue is operational and false if not.
    :problem (only present when :ok? is false) - a string indicating the problem.

    This function handles exceptions and timeouts internally."))

(defn- try-to-publish
  "Attempts to publish messages to the given exchange.

  When the RabbitMQ server is down or unreachable, calls to publish will throw an exception. Rather
  than raise an error to the caller immediately, the publication will be retried indefinitely.
  By retrying, routine maintenance such as restarting the RabbitMQ server will not result in any
  ingest errors returned to the provider.

  Returns true if the message was successfully enqueued and false otherwise."
  [queue-broker exchange-name msg]
  (when-not (try
              (publish-to-exchange queue-broker exchange-name msg)
              (catch Exception e
                (error e)
                false))
    (warn "Attempt to queue messaged failed. Retrying: " msg)
    (Thread/sleep 2000)
    (recur queue-broker exchange-name msg)))

(defn-timed publish-message
  "Publishes a message to an exchange Throws a service unavailable error if the message
  fails to be put on the queue.

  Requests to publish a message are wrapped in a timeout to handle error cases with the Rabbit MQ
  server. Otherwise failures to publish will be retried indefinitely."
  [queue-broker exchange-name msg]
  (let [start-time (System/currentTimeMillis)]
    (try
      (timeout/thunk-timeout #(try-to-publish queue-broker exchange-name msg) (config/publish-queue-timeout-ms))
      (catch java.util.concurrent.TimeoutException e
        (errors/throw-service-error
          :service-unavailable
          (str "Request timed out when attempting to publish message: " msg)
          e)))))
