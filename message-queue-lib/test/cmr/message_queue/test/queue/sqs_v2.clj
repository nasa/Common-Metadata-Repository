(ns cmr.message-queue.test.queue.sqs-v2
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is testing]]
   [cmr.common.test.test-util :refer [with-env-vars]]
   [cmr.message-queue.queue.memory-queue :as memory]
   [cmr.message-queue.queue.names :as names]
   [cmr.message-queue.queue.queue-broker :as broker]
   [cmr.message-queue.queue.sqs :as sqs]
   [cmr.message-queue.queue.sqs-v2 :as sqs-v2])
  (:import
   (software.amazon.awssdk.auth.credentials AnonymousCredentialsProvider)
   (software.amazon.awssdk.regions Region)
   (software.amazon.awssdk.services.sqs SqsClient)))

(deftest shared-name-normalization-test
  (with-env-vars {"CMR_APP_ENVIRONMENT" "sit"}
    (is (= "gsfc-eosdis-cmr-sit-bootstrap_queue"
           (names/normalize-queue-name "cmr.bootstrap.queue")))
    (is (= (#'sqs/normalize-queue-name "cmr.bootstrap.queue")
           (names/normalize-queue-name "cmr.bootstrap.queue")))))

(deftest local-client-configuration-test
  (with-open [client (.build (-> (SqsClient/builder)
                                 (.credentialsProvider (AnonymousCredentialsProvider/create))
                                 (sqs-v2/configure-builder "http://localhost:9324")))]
    (is (= Region/US_EAST_1 (.. client serviceClientConfiguration region)))))

(deftest queue-policy-test
  (let [queue-arn "arn:aws:sqs:us-east-1:123:queue"
        topic-arn "arn:aws:sns:us-east-1:123:topic"
        policy (json/decode (sqs-v2/queue-policy queue-arn [topic-arn]) true)
        statement (first (:Statement policy))]
    (is (= "2012-10-17" (:Version policy)))
    (is (= "SQS:SendMessage" (:Action statement)))
    (is (= queue-arn (:Resource statement)))
    (is (= topic-arn (get-in statement [:Condition :ArnEquals :aws:SourceArn])))))

(deftest broker-factory-selection-test
  (testing "memory remains the existing broker"
    (with-redefs [cmr.message-queue.config/queue-type (constantly "memory")]
      (is (instance? cmr.message_queue.queue.memory_queue.MemoryQueueBroker
                     (broker/create-v2-queue-broker {})))))
  (testing "AWS selects SDK v2 only for the explicit v2 factory"
    (with-redefs [cmr.message-queue.config/queue-type (constantly "aws")]
      (is (instance? cmr.message_queue.queue.sqs_v2.SQSQueueBrokerV2
                     (broker/create-v2-queue-broker {})))
      (is (instance? cmr.message_queue.queue.sqs.SQSQueueBroker
                     (broker/create-queue-broker {}))))))
