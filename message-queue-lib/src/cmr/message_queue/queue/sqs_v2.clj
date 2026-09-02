(ns cmr.message-queue.queue.sqs-v2
  "AWS SDK v2 implementation of the CMR Queue protocol."
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.string :as string]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [debug error info warn]]
   [cmr.message-queue.queue.names :as names]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol])
  (:import
   (java.net URI)
   (software.amazon.awssdk.auth.credentials AwsBasicCredentials StaticCredentialsProvider)
   (software.amazon.awssdk.regions Region)
   (software.amazon.awssdk.services.sns SnsClient)
   (software.amazon.awssdk.services.sns.model CreateTopicRequest ListSubscriptionsByTopicRequest
                                               ListTopicsRequest PublishRequest
                                               SetSubscriptionAttributesRequest SubscribeRequest)
   (software.amazon.awssdk.services.sqs SqsClient)
   (software.amazon.awssdk.services.sqs.model CreateQueueRequest DeleteMessageRequest
                                               GetQueueAttributesRequest GetQueueUrlRequest
                                               PurgeQueueRequest QueueAttributeName
                                               ReceiveMessageRequest SendMessageRequest
                                               SetQueueAttributesRequest)))

(defconfig queue-polling-timeout
  "Seconds to wait for an SQS long poll."
  {:default 20 :type Long})
(defconfig default-queue-visibility-timeout
  "Default visibility timeout."
  {:default 300 :type Long})
(defconfig provider-queue-visibility-timeout
  "Provider queue visibility timeout."
  {:default 43200 :type Long})
(defconfig default-num-tries
  "Default receive attempts before redrive."
  {:default 5 :type Long})
(defconfig sqs-endpoint
  "Optional SQS endpoint override."
  {:default nil :type String})
(defconfig sns-endpoint
  "Optional SNS endpoint override."
  {:default nil :type String})
(defconfig sqs-extend-policy-remaining-exchanges
  "Whether additional topic bindings extend the queue policy."
  {:default true :type Boolean})

(defn queue-visibility-timeout [queue-name]
  (if (string/includes? queue-name "provider")
    (provider-queue-visibility-timeout)
    (default-queue-visibility-timeout)))

(defn dead-letter-queue [normalized-name]
  (str normalized-name "_dead_letter_queue"))

(defn arn->name [arn]
  (string/replace arn #".*:" ""))

(defn subscription-endpoint->name [endpoint]
  (if (string/starts-with? endpoint "http")
    (last (string/split endpoint #"/"))
    (arn->name endpoint)))

(defn configure-builder
  "Applies an endpoint override and the fixed local region when endpoint is configured."
  [builder endpoint]
  (if endpoint
    (-> builder
        (.endpointOverride (URI/create endpoint))
        (.region Region/US_EAST_1)
        (.credentialsProvider
         (StaticCredentialsProvider/create (AwsBasicCredentials/create "local" "local"))))
    builder))

(defn create-aws-client [type]
  (case type
    :sqs (.build (configure-builder (SqsClient/builder) (sqs-endpoint)))
    :sns (.build (configure-builder (SnsClient/builder) (sns-endpoint)))))

(defn queue-url [^SqsClient client normalized-name]
  (.queueUrl (.getQueueUrl client (-> (GetQueueUrlRequest/builder)
                                      (.queueName normalized-name) .build))))

(defn queue-arn [^SqsClient client url]
  (let [response (.getQueueAttributes
                  client (-> (GetQueueAttributesRequest/builder)
                             (.queueUrl url)
                             (.attributeNames [QueueAttributeName/QUEUE_ARN])
                             .build))]
    (get (.attributes response) QueueAttributeName/QUEUE_ARN)))

(defn create-queue!
  "Idempotently creates a queue and DLQ, then applies visibility and redrive attributes."
  [^SqsClient client queue-name max-tries visibility-timeout]
  (let [name (names/normalize-queue-name queue-name)
        dlq-name (dead-letter-queue name)
        dlq-url (.queueUrl (.createQueue client (-> (CreateQueueRequest/builder)
                                                    (.queueName dlq-name) .build)))
        dlq-arn (queue-arn client dlq-url)
        url (.queueUrl (.createQueue client (-> (CreateQueueRequest/builder)
                                                (.queueName name) .build)))
        attrs {QueueAttributeName/VISIBILITY_TIMEOUT (str visibility-timeout)
               QueueAttributeName/REDRIVE_POLICY
               (json/generate-string {:maxReceiveCount (str max-tries)
                                      :deadLetterTargetArn dlq-arn})}]
    (.setQueueAttributes client (-> (SetQueueAttributesRequest/builder)
                                    (.queueUrl url) (.attributes attrs) .build))
    url))

(defn create-topic! [^SnsClient client exchange-name]
  (.topicArn (.createTopic client (-> (CreateTopicRequest/builder)
                                      (.name (names/normalize-queue-name exchange-name)) .build))))

(defn queue-policy [queue-arn topic-arns]
  (json/generate-string
   {:Version "2012-10-17"
    :Statement (mapv (fn [topic-arn]
                       {:Sid (str "Allow-" (Math/abs (hash topic-arn)))
                        :Effect "Allow"
                        :Principal {:Service "sns.amazonaws.com"}
                        :Action "SQS:SendMessage"
                        :Resource queue-arn
                        :Condition {:ArnEquals {"aws:SourceArn" topic-arn}}})
                     topic-arns)}))

(defn bind-queue-to-exchanges!
  [^SnsClient sns ^SqsClient sqs exchange-names queue-name]
  (let [url (queue-url sqs (names/normalize-queue-name queue-name))
        q-arn (queue-arn sqs url)
        topic-arns (mapv #(create-topic! sns %) exchange-names)
        policy-topics (if (sqs-extend-policy-remaining-exchanges)
                        topic-arns
                        (take-last 1 topic-arns))]
    (.setQueueAttributes sqs (-> (SetQueueAttributesRequest/builder)
                                 (.queueUrl url)
                                 (.attributes {QueueAttributeName/POLICY
                                               (queue-policy q-arn policy-topics)})
                                 .build))
    (doseq [topic-arn topic-arns]
      (let [sub-arn (.subscriptionArn
                     (.subscribe sns (-> (SubscribeRequest/builder)
                                         (.topicArn topic-arn) (.protocol "sqs")
                                         (.endpoint q-arn) .build)))]
        (.setSubscriptionAttributes
         sns (-> (SetSubscriptionAttributesRequest/builder)
                 (.subscriptionArn sub-arn) (.attributeName "RawMessageDelivery")
                 (.attributeValue "true") .build))))))

(defn get-topic-arn [^SnsClient client exchange-name]
  (let [wanted (names/normalize-queue-name exchange-name)]
    (loop [token nil]
      (let [builder (ListTopicsRequest/builder)
            _ (when token (.nextToken builder token))
            response (.listTopics client (.build builder))
            found (some #(when (= wanted (arn->name (.topicArn %))) (.topicArn %))
                        (.topics response))]
        (or found (when-let [next-token (.nextToken response)] (recur next-token)))))))

(defn create-async-handler
  ([broker queue-name handler] (create-async-handler broker queue-name handler true))
  ([broker queue-name handler auto-reconnect?]
   (info "Starting listener for queue:" queue-name)
   (let [name (names/normalize-queue-name queue-name)
         url (queue-url @(:sqs-client-atom broker) name)
         request (-> (ReceiveMessageRequest/builder) (.queueUrl url)
                     (.maxNumberOfMessages (int 1))
                     (.waitTimeSeconds (int (queue-polling-timeout))) .build)]
     (async/thread
       (loop []
         (try
           (when-let [message (first (.messages (.receiveMessage
                                                ^SqsClient @(:sqs-client-atom broker) request)))]
             (try
               (handler (json/decode (.body message) true))
               (.deleteMessage ^SqsClient @(:sqs-client-atom broker)
                               (-> (DeleteMessageRequest/builder) (.queueUrl url)
                                   (.receiptHandle (.receiptHandle message)) .build))
               (catch Throwable e
                 (error e "Message processing failed for message" (pr-str message)
                        "on queue" name))))
           (catch Throwable e
             (if (= "cmr.message_queue.test.ExitException" (.getName (class e)))
               (do
                 (error "Async handler for queue" name "exiting.")
                 (throw e))
               (do
                 (error e "Async handler for queue" name "continuing after failed receive.")
                 (Thread/sleep 1000)
                 (when auto-reconnect?
                   (warn "Recreating SQS v2 client.")
                   (let [old @(:sqs-client-atom broker)
                         replacement (create-aws-client :sqs)]
                     (reset! (:sqs-client-atom broker) replacement)
                     (.close ^SqsClient old)))))))
         (recur))))))

(defrecord SQSQueueBrokerV2
  [sns-client-atom sqs-client-atom queues normalized-queue-names exchanges
   queues-to-policies queues-to-exchanges]
  lifecycle/Lifecycle
  (start [this _]
    (let [sqs (create-aws-client :sqs)
          sns (create-aws-client :sns)]
      (try
        (doseq [queue queues]
          (create-queue! sqs queue
                         (get-in queues-to-policies [queue :max-tries] (default-num-tries))
                         (get-in queues-to-policies [queue :visibility-timeout-secs]
                                 (queue-visibility-timeout queue))))
        (doseq [exchange exchanges] (create-topic! sns exchange))
        (doseq [[queue bound-exchanges] queues-to-exchanges]
          (bind-queue-to-exchanges! sns sqs bound-exchanges queue))
        (assoc this
               :sqs-client-atom (atom sqs)
               :sns-client-atom (atom sns)
               :normalized-queue-names
               (into {} (map (juxt names/normalize-queue-name identity) queues)))
        (catch Throwable e
          (.close sqs)
          (.close sns)
          (throw e)))))
  (stop [this _]
    (when sns-client-atom (.close ^SnsClient @sns-client-atom))
    (when sqs-client-atom (.close ^SqsClient @sqs-client-atom))
    this)
  queue-protocol/Queue
  (publish-to-queue [_ queue-name msg]
    (let [client ^SqsClient @sqs-client-atom
          url (queue-url client (names/normalize-queue-name queue-name))]
      (.sendMessage client (-> (SendMessageRequest/builder) (.queueUrl url)
                               (.messageBody (json/generate-string msg)) .build))))
  (get-queues-bound-to-exchange [_ exchange-name]
    (let [client ^SnsClient @sns-client-atom
          topic-arn (get-topic-arn client exchange-name)]
      (loop [token nil result []]
        (let [builder (-> (ListSubscriptionsByTopicRequest/builder) (.topicArn topic-arn))
              _ (when token (.nextToken builder token))
              response (.listSubscriptionsByTopic client (.build builder))
              names-found (map #(get normalized-queue-names
                                     (subscription-endpoint->name (.endpoint %))
                                     (subscription-endpoint->name (.endpoint %)))
                               (.subscriptions response))
              result (into result names-found)]
          (if-let [next-token (.nextToken response)]
            (recur next-token result)
            result)))))
  (publish-to-exchange [_ exchange-name msg]
    (let [client ^SnsClient @sns-client-atom]
      (.publish client (-> (PublishRequest/builder)
                           (.topicArn (get-topic-arn client exchange-name))
                           (.message (json/generate-string msg)) .build))))
  (subscribe [this queue-name handler]
    (create-async-handler this queue-name handler))
  (reset [_]
    (let [client ^SqsClient @sqs-client-atom]
      (doseq [queue queues
              name [(names/normalize-queue-name queue)
                    (dead-letter-queue (names/normalize-queue-name queue))]]
        (.purgeQueue client (-> (PurgeQueueRequest/builder)
                                (.queueUrl (queue-url client name)) .build)))))
  (reconnect [this]
    (warn "Recreating SNS v2 client.")
    (let [old @sns-client-atom
          replacement (create-aws-client :sns)]
      (reset! sns-client-atom replacement)
      (.close ^SnsClient old)
      this))
  (health [this]
    (try
      (queue-protocol/get-queues-bound-to-exchange this (first exchanges))
      {:ok? true}
      (catch Throwable e {:ok? false :msg (.getMessage e)}))))

(record-pretty-printer/enable-record-pretty-printing SQSQueueBrokerV2)

(defn create-queue-broker [{:keys [queues exchanges queues-to-policies queues-to-exchanges]}]
  (->SQSQueueBrokerV2 nil nil queues nil exchanges queues-to-policies queues-to-exchanges))
