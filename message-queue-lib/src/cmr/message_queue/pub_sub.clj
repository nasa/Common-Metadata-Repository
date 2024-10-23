(ns cmr.message-queue.pub-sub 
  (:require
   [cmr.message-queue.config :as config]
   #_{:clj-kondo/ignore [:unused-namespace]}
   [cmr.message-queue.topic.aws-topic :as aws-topic]
   [cmr.message-queue.topic.local-topic :as local-topic]))

(defn create-topic
  "Create a topic using the given topic configuration. The type is determined
    by the environment variable CMR_QUEUE_TYPE."
  []
  (let [create-fn (case (config/queue-type)
                    "memory" local-topic/setup-topic nil
                    "aws" aws-topic/setup-topic (config/cmr-internal-subscriptions-topic-name))]
    (create-fn)))
