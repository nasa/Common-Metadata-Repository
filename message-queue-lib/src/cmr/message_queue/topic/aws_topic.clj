(ns cmr.message-queue.topic.aws-topic
  "Defines an AWS implementation of the topic protocol."
  (:require
   [cheshire.core :as json]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.log :refer [error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.message-queue.config :as config]
   [cmr.message-queue.queue.aws-queue :as aws-queue]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol])
  (:import
   (software.amazon.awssdk.regions Region)
   (software.amazon.awssdk.services.sns SnsClient)
   (software.amazon.awssdk.services.sns.model CreateTopicRequest)
   (software.amazon.awssdk.services.sns.model CreateTopicResponse)
   (software.amazon.awssdk.services.sns.model MessageAttributeValue)
   (software.amazon.awssdk.services.sns.model PublishRequest)
   (software.amazon.awssdk.services.sns.model SetSubscriptionAttributesRequest)
   (software.amazon.awssdk.services.sns.model SubscribeRequest)
   (software.amazon.awssdk.services.sns.model UnsubscribeRequest)))

(defn attribute-builder
  "Create an AWS attribute based on the passed in value to use when
  publishing messages."
  [attr-value]
  (cond
    (string? attr-value) (-> (MessageAttributeValue/builder)
                             (.dataType "String")
                             (.stringValue attr-value)
                             (.build))
    (number? attr-value) (-> (MessageAttributeValue/builder)
                             (.dataType "Number")
                             ;; yes use .stringValue for numbers.
                             (.stringValue (str attr-value))
                             (.build))
    :else nil))

(defn attributes-builder
  "Build a map of attributes if they exist to send along with messages. The
  passed in attribute map is in the following form:
   {\"collection-concept-id\" \"C12345-PROV1\"
    \"mode\" \"New\"}
  A map consisting of string keys and MessageAttributeValue AWS SDK java 2
  objects are returned."
  [attribute-map]
  (let [attr-keys (keys attribute-map)]
    (loop [new-keys attr-keys
           result {}]
      (let [attr-key (first new-keys)
            attr-value (attribute-builder (attribute-map attr-key))]
        (if attr-value
          (if (nil? (seq (rest new-keys)))
            (conj result {attr-key attr-value})
            (recur (rest new-keys) (conj result {attr-key attr-value})))
          result)))))

(defn subscribe-sqs-to-sns
  "Subscribes an AWS SQS to an AWS SNS Topic."
  [sns-client topic-arn sqs-arn]
  (let [sub-request (-> (SubscribeRequest/builder)
                        (.protocol "sqs")
                        (.endpoint sqs-arn)
                        (.returnSubscriptionArn true)
                        (.topicArn topic-arn)
                        (.build))
        response (.subscribe sns-client sub-request)]
    (.subscriptionArn response)))

(defn set-filter-policy
  "For a given subscription set the filter policy so that the queue
    only gets the notificiation messages that it wants. The passed in
    filter policy is a hash map - for example:
    {\"collection-concept-id\": \"C12345-PROV1\"
     \"mode\": [\"New\", \"Update\"]}"
  [sns-client subscription-arn subscription-metadata]
  ;; Turn the clojure filter policy to json
  (when (or (:CollectionConceptId subscription-metadata)
            (:Mode subscription-metadata))
    (let [filters (util/remove-nil-keys
                   {:collection-concept-id [(:CollectionConceptId subscription-metadata)]
                    :mode (:Mode subscription-metadata)
                    :subscriber [(:SubscriberId subscription-metadata)]})
          filter-json (json/generate-string filters)
          sub-filter-request (-> (SetSubscriptionAttributesRequest/builder)
                                 (.subscriptionArn subscription-arn)
                                 (.attributeName "FilterPolicy")
                                 (.attributeValue filter-json)
                                 (.build))]
      (.setSubscriptionAttributes sns-client sub-filter-request))))

(defn set-redrive-policy
  "For a given subscription set the redrive-policy - which is a dead letter queue if the
    message cannot be sent from the SNS to the subscribed endpoint."
  [sns-client subscription-arn dead-letter-queue-arn]
  (let [redrive-policy (str "{\"deadLetterTargetArn\": \"" dead-letter-queue-arn "\"}")
        _ (println "redrive-policy:" redrive-policy)
        sqs-request (-> (SetSubscriptionAttributesRequest/builder)
                        (.subscriptionArn subscription-arn)
                        (.attributeName "RedrivePolicy")
                        (.attributeValue redrive-policy)
                        (.build))]
    (.setSubscriptionAttributes sns-client sqs-request)))

(defrecord
 AWSTopic
 [;; A record containing fields related to accessing SNS topics.
  ;; Connection to AWS SNS
  ^SnsClient sns-client
  ;; The endpoint of the topic to send messages to. For AWS it is the topic ARN,
  ;; for the in memory implementation it is nil.
  topic-arn

  subscription-dead-letter-queue-arn]

  topic-protocol/Topic
  (subscribe
    [_this subscription]
    (try
      (let [subscription-arn (subscribe-sqs-to-sns sns-client topic-arn (get-in subscription [:metadata :EndPoint]))]
        (when subscription-arn
          (set-filter-policy sns-client subscription-arn (:metadata subscription))
          (set-redrive-policy sns-client subscription-arn subscription-dead-letter-queue-arn))
        subscription-arn)
      (catch Exception e
        (let [msg (format "Exception caught trying to subscribe the queue %s to the %s SNS Topic. Exception: %s"
                          (get-in subscription [:metadata :EndPoint])
                          topic-arn
                          (.getMessage e))]
          (error msg)
          (errors/throw-service-error :invalid-data msg)))))

  (unsubscribe
   [_this subscription]
   (let [sub-request (-> (UnsubscribeRequest/builder)
                         (.subscriptionArn (:subscription-arn subscription))
                         (.build))]
     (.unsubscribe sns-client sub-request))
   (:subscription-arn subscription))

  (publish
    [_this message message-attributes subject]
    (let [msg-atts (attributes-builder message-attributes)
          pub-request (-> (PublishRequest/builder)
                          (.message message)
                          (.subject subject)
                          (.topicArn topic-arn)
                          (.messageAttributes msg-atts)
                          (.build))]
      (.publish sns-client pub-request)))

  (health
   [_this]
   {:ok? true}))
(record-pretty-printer/enable-record-pretty-printing AWSTopic)

(defn create-sns-client
  "Create an AWS SNS service client."
  []
  (-> (SnsClient/builder)
      (.region Region/US_EAST_1)
      (.build)))

(defn create-sns-topic
  "Create an SNS topic in AWS."
  [sns-client sns-name]
  (try
    (let [sns-request (-> (CreateTopicRequest/builder)
                          (.name sns-name)
                          (.build))
          response (.createTopic sns-client sns-request)]
      (.topicArn ^CreateTopicResponse response))
    (catch Exception e
      (error (format "Exception caught trying to create the %s SNS topic. Exception %s"
                     sns-name
                     (.getMessage e))))))

(defn setup-topic
  "Set up the AWS topic so that we can store the topic ARN to publish messages."
  [sns-name]
  (println "Setting up AWS-topic")
  (let [sns-client (create-sns-client)
        topic-arn (create-sns-topic sns-client sns-name)
        sqs-client (aws-queue/create-sqs-client)
        sub-dl-queue-url (aws-queue/create-queue sqs-client (config/cmr-subscriptions-dead-letter-queue-name))
        sub-dl-queue-arn (aws-queue/get-queue-arn sqs-client sub-dl-queue-url)]
    (->AWSTopic sns-client topic-arn sub-dl-queue-arn)))

(comment
  (def topic (setup-topic "cmr-internal-subscriptions-sit"))
  (topic-protocol/publish topic "test message" {"subject" "A new concept"
                                                "collection-concept-id" "C12345-PROV1"
                                                "mode" "New"}))
