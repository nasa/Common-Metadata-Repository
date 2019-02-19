(ns cmr.message-queue.queue.queue-broker
  "Provides a single function that will create a queue broker based on the environment."
  (:require
   [cmr.message-queue.config :as config]
   [cmr.message-queue.queue.memory-queue :as memory]
   [cmr.message-queue.queue.sqs :as sqs]))

(defn create-queue-broker
  "Create a queue broker using the given queue configuration. The type is determined
  by the environment variable CMR_QUEUE_TYPE."
  [queue-config]
  (let [create-fn (case (config/queue-type)
                    "memory" memory/create-memory-queue-broker
                    "aws" sqs/create-queue-broker)]
    (create-fn queue-config)))
