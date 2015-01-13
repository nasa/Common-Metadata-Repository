(ns cmr.message-queue.services.queue
  "Declares a protocol for publishing to a queue")

(defprotocol Queue
  "Function for adding messages to a message queue"

  (create-queue
    [this queue-name]
    "Create a queue with the given parameter map")

   (publish
    [this queue-name msg metadata]
    "Publish a message on the queue")

  (subscribe
    [this queue-name handler params]
    "Subscribe to the given queue using the given handler with optonal params"))