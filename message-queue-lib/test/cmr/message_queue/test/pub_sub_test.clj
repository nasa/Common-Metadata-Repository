(ns cmr.message-queue.test.pub-sub-test
  "Namespace to test local sqs server"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.message-queue.config :as config]
   [cmr.message-queue.pub-sub :as pub-sub]
   [cmr.message-queue.queue.aws-queue :as queue]
   [cmr.message-queue.test.test-util :as test-util]
   [cmr.message-queue.topic.aws-topic :as aws-topic]
   [cmr.message-queue.topic.local-topic :as local-topic]
   [cmr.message-queue.topic.topic-protocol :as topic-protocol])
  (:import
   (software.amazon.awssdk.services.sqs.model SqsException)))

(use-fixtures :once test-util/embedded-sqs-server-fixture)

(deftest basic-sqs-server-test
  (testing "Able to reach sqs server..."
    (is (= true (test-util/server-running?))))

  (testing "Checking if a bad queue can be created using a bad url. The funtion returns nil when it can't."
    (let [sqs-client (queue/create-sqs-client "http://someurl.something")]
      (is (nil? (queue/create-queue sqs-client (config/cmr-internal-subscriptions-queue-name))))))

  (let [bad-queue-url "http://localhost:9324/000000000000/cmr-internal-subscriptions-queue-not"
        sqs-client (queue/create-sqs-client (config/sqs-server-url))
        queue-url (queue/create-queue sqs-client (config/cmr-internal-subscriptions-queue-name))
        msg-attributes (queue/attributes-builder {"collection-concept-id" "C12345-PROV1"
                                                  "Mode" "New"})
        message "This is a test."]

    (testing "Check to see if a queue was created."
      (is (some? queue-url)))

    (testing "Not being able to publish a message."
      (is (thrown? SqsException (queue/publish sqs-client bad-queue-url message msg-attributes))))

    (testing "Publish a message."  
      (is (some? (queue/publish sqs-client queue-url message msg-attributes))))

    (testing "Not reading a message."
      (is (nil? (queue/receive-messages sqs-client bad-queue-url))))
    
    (testing "Not deleting a message."
      (is (nil? (queue/delete-messages sqs-client bad-queue-url ""))))

    (testing "Reading and deleting a message"
      (let [messages (queue/receive-messages sqs-client queue-url)]
        (is (= message (.body (first messages))))
        (is (some? (queue/delete-messages sqs-client queue-url messages)))))

    (testing "Not deleting a queue."
      (is (nil? (queue/delete-queue sqs-client bad-queue-url))))
    
    (testing "Deleting a queue."
      ;; Just in case this test is run against an AWS environment - namely sit
      ;; another queue to delete is created.
      (let [queue-url2 (queue/create-queue sqs-client (str (config/cmr-internal-subscriptions-queue-name) "-2"))]
        (is (some? (queue/delete-queue sqs-client queue-url2)))))))

(deftest creating-message-attributes-test
  (testing "creating attributes with a string"
    (is (= 2 (-> (queue/attributes-builder {"collection-concept-id" "C12345-PROV1"
                                            "Mode" "New"})
                 (keys)
                 (count))))
    (is (= 2 (-> (aws-topic/attributes-builder {"collection-concept-id" "C12345-PROV1"
                                                "Mode" "New"})
                 (keys)
                 (count)))))

  (testing "creating attributes with a number"
    (is (= 2 (-> (queue/attributes-builder {"collection-concept-id" "C12345-PROV1"
                                            "Mode" 1})
                 (keys)
                 (count))))
    (is (= 2 (-> (aws-topic/attributes-builder {"collection-concept-id" "C12345-PROV1"
                                                "Mode" 1})
                 (keys)
                 (count)))))

  (testing "creating attributes with a map - the map should not be included."
    (is (= 1 (-> (queue/attributes-builder {"collection-concept-id" "C12345-PROV1"
                                            "Mode" {1 1}})
                 (keys)
                 (count))))
    (is (= 1 (-> (aws-topic/attributes-builder {"collection-concept-id" "C12345-PROV1"
                                                "Mode" {1 1}})
                 (keys)
                 (count))))))

(deftest subscribe-queue-to-topic
  (when (= "memory" (config/queue-type))
    (let [topic (pub-sub/create-topic "sns-name")
          _ (local-topic/setup-infrastructure topic)
          subscription (first @(:subscription-atom topic))
          message "test"
          message-attributes {"collection-concept-id" "C12345-PROV1"
                              "mode" "New"}
          subject "A new granule"
          sqs-client (:sqs-client subscription)
          queue-url (:queue-url subscription)
          dead-letter-queue-url (:dead-letter-queue-url subscription)]

      (is (= (keys subscription) '(:sqs-client :queue-url :dead-letter-queue-url)))
      (is (some? (topic-protocol/publish topic message message-attributes subject)))

      (when-let [messages (seq (queue/receive-messages sqs-client queue-url))]
        (is (= message (.body (first messages))))
        (is (some? (queue/delete-messages (queue/create-sqs-client (config/sqs-server-url)) queue-url messages))))

      ;; Delete the queue to test the publish sending to dead letter queue.
      (queue/delete-queue sqs-client queue-url)
      (is (some? (topic-protocol/publish topic message message-attributes subject)))
      (when-let [messages (seq (queue/receive-messages sqs-client dead-letter-queue-url))]
        (is (= message (.body (first messages))))
       (is (some? (queue/delete-messages sqs-client dead-letter-queue-url messages)))))))
