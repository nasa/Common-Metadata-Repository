(ns cmr.message-queue.queue.sqs
  "Implements index-queue functionality using Amazon SQS."
  (:gen-class)
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.core.async :as a]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh]
            [cmr.common.util :as u]
            [cmr.message-queue.config :as config]
            [cmr.message-queue.services.queue :as queue])
  (:import com.amazonaws.AmazonClientException
           com.amazonaws.AmazonServiceException
           com.amazonaws.services.sqs.AmazonSQSClient
           com.amazonaws.services.sns.AmazonSNSClient
           com.amazonaws.services.sqs.model.GetQueueUrlResult
           com.amazonaws.services.sqs.model.ReceiveMessageRequest
           com.amazonaws.services.sqs.model.SendMessageResult
           com.amazonaws.ClientConfiguration
           com.amazonaws.Protocol
           com.amazonaws.auth.AWSCredentials
           com.amazonaws.auth.DefaultAWSCredentialsProviderChain
           com.amazonaws.regions.Region
           com.amazonaws.regions.Regions
           java.io.IOException))

; (def topic-arn "arn:aws:sns:us-east-1:985962406024:cmr-indexer")

(defn topic-arn->exchange-name
  "Pull an exchange name out of a topic ARN."
  [arn]
  (clojure.string/replace arn #".*:" ""))

(defn normalize-queue-name
  "Replace dots with underscores"
  [queue-name]
  (clojure.string/replace queue-name "." "_"))

; (def queue-url "https://sqs.us-east-1.amazonaws.com/985962406024/cmr-test-queue")

(defn- get-topic
  "Returns the Topic with the given display name."
  [client exchange-name]
  (let [exchange-name (normalize-queue-name exchange-name)
        topics (into [] (.getTopics (.listTopics client)))]
   (some (fn [topic] (let [topic-arn (.getTopicArn topic)
                           topic-name (topic-arn->exchange-name topic-arn)]
                        (when (= exchange-name topic-name)
                         topic)))
         topics)))

(defn- create-async-handler
  "Creates a go block that will asynchronously pull messages off the queue, pass them to the handler,
  and process the response."
  [queue-broker queue-name handler]
  (info "Starting listener for queue: " queue-name)
  (let [queue-name (normalize-queue-name queue-name)
        client (get queue-broker :sqs-client)
        queue-url (.getQueueUrl (.getQueueUrl client queue-name))
        rec-req (ReceiveMessageRequest. queue-url)
        _ (.setMaxNumberOfMessages rec-req (Integer. 1))
        ;; wait for up to 20 seconds before returning with no data (long polling)
        _ (.setWaitTimeSeconds rec-req (Integer. 20))]
    (a/go
      (try
        (u/while-let
          [rec-result (.receiveMessage client rec-req)]
          ; (Thread/sleep 60000)
          (let [messages (into [] (.getMessages rec-result))]
            (when-let [msg (first messages)]
              (let [msg-body (.getBody msg)
                    msg-content (json/decode msg-body true)]
                (println "MESSAGE RECEIVED: " msg-content)
                (try
                  (handler msg-content)
                  (.deleteMessage client queue-url (.getReceiptHandle msg))
                  (catch Throwable e
                    (error e "Message processing failed for message" (pr-str msg))))))))
        (finally
          (info "Async go handler for queue" queue-name "completing."))))))

(defrecord SQSQueueBroker
 [
   ;; Connection to AWS SNS
   sns-client

   ;; Connection to AWS SQS
   sqs-client]

    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 lifecycle/Lifecycle

  (start
    [this system]
    (-> this
        (assoc :sns-client (AmazonSNSClient.))
        (assoc :sqs-client (AmazonSQSClient.))))

  (stop
    [this system]
    
    this)
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue/Queue

  ;  (publish-to-queue
  ;    [this queue-name msg]
  ;    (let [queue-url (.getQueueUrl (.getQueueUrl sqs-client queue-name))]
  ;      (.sendMessage sqs-client queue-url msg)))

  (publish-to-queue
    [this queue-name msg]
    (println "PUBLISHING TO QUEUE: " queue-name)
    (let [msg (json/generate-string msg)
          queue-name (normalize-queue-name queue-name)
          queue-url (.getQueueUrl (.getQueueUrl sqs-client queue-name))]
      (.sendMessage sqs-client queue-url msg)))

  (get-queues-bound-to-exchange
    [this exchange-name]
    (let [exchange-name (normalize-queue-name exchange-name)
          topic (get-topic sns-client exchange-name)
          topic-arn (.getTopicArn topic)
          subs (into [] (.getSubscriptions (.listSubscriptionsByTopic sns-client topic-arn)))]
      (map #(-> % .getEndpoint topic-arn->exchange-name) subs)))

  (publish-to-exchange
     [this exchange-name msg]
     (let [msg (json/generate-string msg)
           exchange-name (normalize-queue-name exchange-name)
           topic (get-topic sns-client exchange-name)
           topic-arn (.getTopicArn topic)]
      (println "PUBLISHING TO EXCHANGE: " exchange-name)
      (.publish sns-client topic-arn msg)))

  (subscribe
     [this queue-name handler]
     (let [queue-name (normalize-queue-name queue-name)]
      (println "SUBSCRIBING TO QUEUE: " queue-name)
      (create-async-handler this queue-name handler)))

  (reset
     [this])

  (health
     [this]
     {:ok? true}))

(defn create-queue-broker
  "Creates a broker that uses SNS/SQS"
  [{:keys [queues exchanges queues]}]
  (->SQSQueueBroker nil nil))

(comment
  (cmr.system-int-test.utils.ingest-util/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})
  (normalize-queue-name "cmr_access_control_index.queue")

  (def broker (lifecycle/start (create-queue-broker {}) nil))
  (get-topic (:sns-client broker) "cmr_ingest_exchange")
  (.getTopics (.listTopics (:sns-client broker)))
  (queue/get-queues-bound-to-exchange broker "cmr_ingest_exchange")
  (queue/subscribe broker "cmr-test-queue" (fn [msg] (println "Got Message: " msg)))
  (queue/publish-to-queue broker "cmr-test-queue" "{\"body\": \"ABC\"}")

  (def client (AmazonSQSClient.))
  (def queue-url (.getQueueUrl (.getQueueUrl client "cmr-index-queue")))
  (def msg-res (.sendMessage client queue-url "This is a test message"))
  (str msg-res)

  (def rec-msg (.receiveMessage client queue-url))
  (str rec-msg)
  (def messages (.getMessages rec-msg))
  (def rhandle (.getReceiptHandle (.get messages 0)))
  (.deleteMessage client queue-url rhandle)

  (def topic-arn "arn:aws:sns:us-east-1:985962406024:cmr-indexer")
  (def sns-client (AmazonSNSClient.))
  (.publish sns-client topic-arn "This is a test message."))
