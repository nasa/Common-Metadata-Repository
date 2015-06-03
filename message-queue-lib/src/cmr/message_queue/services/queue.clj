(ns cmr.message-queue.services.queue
  "Declares a protocol for creating and interacting with queues and a record that defines
  a queue listener"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]))

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

  (publish-to-exchange
    [this exchange-name msg]
    "Publishes a message on the exchange with the given exchange name. Returns true if the message was
    successfully enqueued. Otherwise returns false.")

  (subscribe
    [this queue-name handler-fn]
    "Subscribes to the given queue using the given handler function.

    'handler-fn' is a function that takes a single parameter (the message) and attempts to
    process it. This function should respond with a map of the of the follwing form:
    {:status status :message message}
    where status is one of (:ok, :retry, :fail) and message is optional.
    :ok    - message was processed successfully
    :retry - message could not be processed and should be re-queued
    :fail  - the message cannot be processed and should not be re-queued")

  (message-count
    [this queue-name]
    "Returns the number of messages on the given queue")

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

