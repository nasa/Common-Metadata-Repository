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
        (when (and (= (message-attributes "collection-concept-id")
                      (filter :collection-concept-id))
                   (or (nil? (:mode filter))
                       (some #(= (message-attributes "mode") %) (:mode filter))))
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

(defn infrastructure_setup?
  "Check to see if the infrastructure has been setup"
  [topic]
  (seq @(:subscription-atom topic)))

(defn setup-infrastructure
  "Set up the local CMR internal subscription queue and dead letter queue and
  subscribe then to the passed in topic. This function assumes that elasticmq
  is up and running, or that the tests will start one."
  [topic]
  (when-not (infrastructure_setup? topic)
    (let [sqs-client (queue/create-sqs-client (config/sqs-server-url))
          subscription {:sqs-client sqs-client
                        :queue-url (queue/create-queue sqs-client (config/cmr-internal-subscriptions-queue-name))
                        :dead-letter-queue-url (queue/create-queue sqs-client (config/cmr-internal-subscriptions-dead-letter-queue-name))}]
      (queue/create-queue sqs-client (config/cmr-subscriptions-dead-letter-queue-name))
      (swap! (:subscription-atom topic) conj subscription))))

(defrecord
 LocalTopic
 [;; An atom containing a list of subscriptions. A subscription is a map that 
  ;; contains a sqs-client, filter, a queue URL, and a dead letter queue URL.
  subscription-atom]

  topic-protocol/Topic
  (subscribe
   [this subscription]
   ;; to speed up development startup, the setup call is here and setup checks first to see if it is already setup.
   ;; Otherwise on startup the system would have to wait for the elasticmq to start before it could continue with setting
   ;; up the database slowing down all the tests.
   (setup-infrastructure this)
   (let [metadata (:metadata subscription)
         sqs-client (queue/create-sqs-client (config/sqs-server-url))
         sub {:sqs-client sqs-client
              :filter (when (or (:CollectionConceptId metadata)
                                (:Mode metadata))
                        {:collection-concept-id (:CollectionConceptId metadata)
                         :mode (:Mode metadata)
                         :subscriber (:SubscriberId metadata)})
              :queue-url (:EndPoint metadata)
              :dead-letter-queue-url (queue/create-queue sqs-client (config/cmr-subscriptions-dead-letter-queue-name))
              :concept-id (:concept-id subscription)}]
     (if-not (seq (filter #(= (:concept-id %) (:concept-id subscription))
                          @subscription-atom))
       (swap! subscription-atom conj sub)
       (let [new-subs (filter #(not= (:concept-id %) (:concept-id subscription)) @subscription-atom)]
         (reset! subscription-atom (conj new-subs sub))))
     ;; instead of the full subscription list, pass back the subscription concept id.
     (:concept-id subscription)))

  (unsubscribe
   [_this subscription]
   ;; remove the subscription from the atom and send back the subscription id, not the atom contents.
   (swap! subscription-atom (fn [subs]
                              (doall
                               (filter #(not= (:concept-id %) (:concept-id subscription))
                                       subs))))
   (:concept-id subscription))

  (publish
   [this message message-attributes _subject]
   ;; to speed up development startup, the setup call is here and setup checks first to see if it is already setup.
   ;; Otherwise on startup the system would have to wait for the elasticmq to start before it could continue with setting
   ;; up the database slowing down all the tests.
   (setup-infrastructure this)
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

(comment
  (def topic (setup-topic))
  (def subscription (setup-infrastructure topic))
  (topic-protocol/publish topic "test" {"test" "test"} "test")
  (:subscription-atom topic))
