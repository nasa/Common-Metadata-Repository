(ns cmr.message-queue.topic.aws-topic
  "Defines an AWS implementation of the topic protocol."
  (:require
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.log :refer [error]]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol])
  (:import
   (software.amazon.awssdk.services.sns SnsClient)
   (software.amazon.awssdk.services.sns.model CreateTopicRequest)
   (software.amazon.awssdk.services.sns.model CreateTopicResponse)
   (software.amazon.awssdk.services.sns.model MessageAttributeValue)
   (software.amazon.awssdk.services.sns.model PublishRequest)))

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

(defrecord
 AWSTopic
 [;; A record containing fields related to accessing SNS topics.
  ;; Connection to AWS SNS
  ^SnsClient sns-client
  ;; The endpoint of the topic to send messages to. For AWS it is the topic ARN,
  ;; for the in memory implementation it is nil.
  topic-arn]

  ;; This will be filled in next sprint. CMR-10141
  topic-protocol/Topic
  (subscribe
    [_this _subscription])

  (publish
    [_this message message-attributes]
    (let [msg-atts (attributes-builder message-attributes)
          pub-request (-> (PublishRequest/builder)
                          (.message message)
                          (.subject (:subject message-attributes))
                          (.topicArn topic-arn)
                          (.messageAttributes msg-atts)
                          (.build))]
      (.publish sns-client pub-request)))

  (health
   [_this]
   {:ok? true}))
(record-pretty-printer/enable-record-pretty-printing AWSTopic)

(def create-sns-client
  "Create an AWS SNS service client."
  (.build (SnsClient/builder)))

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
  (let [sns-client create-sns-client
        topic-arn (create-sns-topic sns-client sns-name)]
    (->AWSTopic sns-client topic-arn)))

(comment
  (def topic (setup-topic "cmr-internal-subscriptions-sit"))
  (topic-protocol/publish topic "test message" {"subject" "A new concept"
                                                "collection-concept-id" "C12345-PROV1"
                                                "mode" "New"}))
