(ns cmr.message-queue.services.queue
  "Declares a protocol for creating and interacting with queues")

(defprotocol Queue
  "Functions for adding messages to a message queue"

  (create-queue
    [this queue-name]
    "Creates a queue with the given parameter map")

   (publish
    [this queue-name msg]
    "Publishes a message on the queue")

  (subscribe
    [this queue-name handler params]
    "Subscribes to the given queue using the given handler with optonal params")

  (message-count
    [this queue-name]
    "Returns the number of messages on the given queue")

  (purge-queue
    [this queue-name]
    "Removes all the messages on a queue")

  (delete-queue
    [this queue-name]
    "Deletes the queue with the given name")

  (ack
    [this ch delivery-tag]
    "Acknowledges receipt of a message by a consumer")

  (nack
    [this ch delivery-tag multiple requeue]
    "Negative acknowledgement of one or more messages with optional requeueing"))