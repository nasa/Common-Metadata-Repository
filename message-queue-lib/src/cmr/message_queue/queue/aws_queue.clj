(ns cmr.message-queue.queue.aws-queue
  "Includes both the aws and local queue functions because they both
  can use the AWS SDK Java2 libararies. The local queue uses the
  dockerized elasticMQ."
  (:require
   [cmr.common.log :refer [error]])
  (:import
   (software.amazon.awssdk.regions Region)
   (software.amazon.awssdk.services.sqs SqsClient)
   (software.amazon.awssdk.services.sqs.model CreateQueueRequest)
   (software.amazon.awssdk.services.sqs.model CreateQueueResponse)
   (software.amazon.awssdk.services.sqs.model DeleteMessageRequest)
   (software.amazon.awssdk.services.sqs.model DeleteQueueRequest)
   (software.amazon.awssdk.services.sqs.model MessageAttributeValue)
   (software.amazon.awssdk.services.sqs.model ReceiveMessageRequest)
   (software.amazon.awssdk.services.sqs.model ReceiveMessageResponse)
   (software.amazon.awssdk.services.sqs.model SendMessageRequest)
   (software.amazon.awssdk.services.sqs.model SqsException)))

(defn create-sqs-client
  "Create an SQS service client using AWS SDK java 2 classes. This
  function can also be used to connect to elasticMQ for testing."
  ([]
   (-> (SqsClient/builder)
       (.region Region/US_EAST_1)
       (.build)))
  ([override-endpoint-url]
   (-> (SqsClient/builder)
       (.region Region/US_EAST_1)
       (.endpointOverride (java.net.URI. override-endpoint-url))
       (.build))))

(defn create-queue
  "Create an instance of a an AWS queue in either AWS or elasticMQ."
  [sqs-client queue-name]
  (try
    (let [sqs-request (-> (CreateQueueRequest/builder)
                          (.queueName queue-name)
                          (.build))
          response (.createQueue sqs-client sqs-request)
          queue-url (.queueUrl ^CreateQueueResponse response)]
      queue-url)
    (catch Exception e
      (error (format "Exception caught trying to create the %s SQS queue. Exception: %s"
                     queue-name
                     (.getMessage e))))))

(defn delete-queue
  "Delete an instance of a an AWS queue in either AWS or elasticMQ."
  [sqs-client queue-url]
  (try
    (let [sqs-request (-> (DeleteQueueRequest/builder)
                          (.queueUrl queue-url)
                          (.build))
          response (.deleteQueue sqs-client sqs-request)]
      response)
    (catch Exception e
      (error (format "Exception caught trying to delete the %s SQS queue. Exception: %s"
                     queue-url
                     (.getMessage e))))))

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
    \"mode\" \"new\"}
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

(defn publish
  "Publish a message to the passed in queue-url through the sqs client."
  [sqs-client queue-url message message-attributes]
  (try
    (let [message-request (if message-attributes
                            (-> (SendMessageRequest/builder)
                                (.queueUrl queue-url)
                                (.messageBody message)
                                (.messageAttributes message-attributes)
                                (.build))
                            (-> (SendMessageRequest/builder)
                                (.queueUrl queue-url)
                                (.messageBody message)
                                (.build)))]
      (.sendMessage sqs-client message-request))
    (catch SqsException e
      (error (format "Exception caught trying to publish a message on the %s SQS queue. Exception: %s"
                     queue-url
                     (.getMessage e)))
      (throw e))))

(defn receive-messages
  "Retrieve messages from a queue represented by the queue-url."
  [sqs-client queue-url]
  (try
    (let [sqs-request (-> (ReceiveMessageRequest/builder)
                          (.queueUrl queue-url)
                          (.maxNumberOfMessages (Integer/valueOf (int 1)))
                          (.build))]
      (.messages ^ReceiveMessageResponse (.receiveMessage sqs-client sqs-request)))
    (catch SqsException e
      (error (format "Exception caught trying to receive messages from the %s SQS queue. Exception: %s"
                     queue-url
                     (.getMessage e))))))

(defn delete-messages
  "Delete messages that are on the queue, they have been processed and are no longer needed."
  [sqs-client queue-url messages]
  (try
    (seq
     (map #(let [sqs-request (-> (DeleteMessageRequest/builder)
                                 (.queueUrl queue-url)
                                 (.receiptHandle (.receiptHandle %))
                                 (.build))]
             (.deleteMessage sqs-client sqs-request))
          messages))
    (catch SqsException e
      (error (format "Exception caught trying to delete messages from the %s SQS queue. Exception: %s"
                     queue-url
                     (.getMessage e))))))

(comment
  
  (let [sqs-client (create-sqs-client (cmr.message-queue.config/sqs-server-url))
        queue-url  (create-queue sqs-client (cmr.message-queue.config/cmr-internal-subscriptions-queue-name))
        message-attributes (attributes-builder {"collection-concept-id" "C12345-PROV1"})
        message "A test message"
        _ (publish sqs-client queue-url message message-attributes)
        messages (receive-messages sqs-client queue-url)]
  
    (map #(do (println (.body %))
              (println (.receiptHandle %))
              (println (.messageAttributes %)))
         messages)
    (delete-messages sqs-client queue-url messages))
  )