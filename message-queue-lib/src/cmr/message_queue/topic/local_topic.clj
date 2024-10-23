(ns cmr.message-queue.topic.local-topic
  "Defines a local - non AWS implementation of the Queue protocol. It uses
  a dockerized elasticMQ."
  (:require
   [cmr.common.log :refer [error info]]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.message-queue.config :as config]
   [cmr.message-queue.queue.aws-queue :as queue]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol])
  (:import
   (software.amazon.awssdk.services.sqs.model SqsException)))

(defn publish-message
  "Go through the subscriptions and publish the message to each queue that
  has a filter that matches the message-attributes. If the queue doesn't have
  a filter, then pass the message to them. Returns a list of AWS publish responses."
  [subscription message message-attributes]
  (let [{:keys [sqs-client filter queue-url dead-letter-queue-url]} subscription
        msg-atts (queue/attributes-builder message-attributes)]
    (try
      (if filter
        (when (and (= (:collection-concept-id message-attributes)
                      (:collection-concept-id filter))
                   (or (nil? (:mode filter))
                       (some #(= (:mode message-attributes) %) (:mode filter))))
         (queue/publish sqs-client queue-url message msg-atts))
        (queue/publish sqs-client queue-url message msg-atts))
      (catch SqsException e
        (info (format "Exception caught publishing message to %s. Exception: %s. Please check if queue exists. Send message to %s."
                      (.getMessage e)
                      queue-url
                      dead-letter-queue-url))
        (try
          (queue/publish sqs-client dead-letter-queue-url message msg-atts)
          (catch SqsException e
            (error (format "Exception caught publishing message to %s. Exception: %s. Please check if queue exists. Message droppped."
                           (.getMessage e)
                           dead-letter-queue-url))))))))

(defrecord
 LocalTopic
 [;; An atom containing a list of subscriptions. A subscription is a map that 
  ;; contains a sqs-client, filter, a queue URL, and a dead letter queue URL.
  subscription-atom]

  topic-protocol/Topic
  (subscribe
    [_this subscription]
    (swap! subscription-atom conj subscription))

  (publish
   [_this message message-attributes]
   (doall (map #(publish-message % message message-attributes) @subscription-atom)))
  
  (health
   [_this]
   {:ok? true}))
(record-pretty-printer/enable-record-pretty-printing LocalTopic)

(defn setup-topic
  "Sets up a local topic that mimics AWS SNS to hold subscriptions and send
  messages to the entities (queues) that have subscribed to the topic."
  []
  (->LocalTopic (atom '())))

(defn setup-infrastructure
  "Set up the local CMR internal subscription queue and dead letter queue and
  subscribe then to the passed in topic. This function assumes that elasticmq
  is up and running, or that the tests will start one."
  [topic]
  (let [sqs-client (queue/create-sqs-client (config/sqs-server-url))
        queue-url (queue/create-queue sqs-client (config/cmr-internal-subscriptions-queue-name))
        dl-queue-url (queue/create-queue sqs-client (config/cmr-internal-subscriptions-dead-letter-queue-name))]
    (topic-protocol/subscribe topic {:sqs-client sqs-client
                                     :filter nil
                                     :queue-url queue-url
                                     :dead-letter-queue-url dl-queue-url})))

(comment
  (def topic (setup-topic))
  (def subscription (setup-infrastructure topic))
  (topic-protocol/publish topic "test" {"test" "test"})
  (:subscription-atom topic))
