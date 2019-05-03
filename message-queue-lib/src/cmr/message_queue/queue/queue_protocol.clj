(ns cmr.message-queue.queue.queue-protocol
  "Defines the Queue protocol.")

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

  (reconnect
    [this]
    "Reinitiates the connection to the underlying exchanges. Used when there appears to
    be a systemic problem reaching the exchanges.")

  (health
    [this]
    "Checks to see if the queue is up and functioning properly. Returns a map with the
    following keys/values:
    :ok? - set to true if the queue is operational and false if not.
    :problem (only present when :ok? is false) - a string indicating the problem.

    This function handles exceptions and timeouts internally."))
