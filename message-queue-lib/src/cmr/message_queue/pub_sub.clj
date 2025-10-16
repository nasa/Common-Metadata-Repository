(ns cmr.message-queue.pub-sub
  (:require
   [cmr.message-queue.config :as config]
   [cmr.message-queue.topic.aws-topic :as aws-topic]
   [cmr.message-queue.topic.local-topic :as local-topic]))

(defn create-topic
  "Create a topic using the given topic configuration. The type is determined
    by the environment variable CMR_QUEUE_TYPE."
  [sns-name]
  (case (config/queue-type)
    "memory" (local-topic/setup-topic)
    "aws" (aws-topic/setup-topic sns-name)))
