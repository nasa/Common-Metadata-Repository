(ns cmr.message-queue.test.queue.sqs
  (:require 
   [clojure.test :refer :all]
   [cmr.common.test.test-util :refer [with-env-vars]]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.queue.sqs :as sqs]
   [cmr.message-queue.test.queue-broker-wrapper :as broker-wrapper])
  (:import
   (cmr.message_queue.test ExitException)
   (com.amazonaws.services.sqs AmazonSQSClient)
   (com.amazonaws.services.sqs.model GetQueueUrlResult)
   (com.amazonaws.services.sqs.model ReceiveMessageResult)))

(defn normalize-queue-name
  "Function to call private normalize-queue-name function."
  [queue-name]
  #'sqs/normalize-queue-name queue-name)

(deftest normalize-queue-name
  (testing "with-and-without-prefix"
    (are3
      [environ queue-name norm-name]
      (with-env-vars {"CMR_APP_ENVIRONMENT" environ}
        (is (= norm-name (#'sqs/normalize-queue-name  queue-name))))

      "SIT with prefix already applied"
      "sit" "gsfc-eosdis-cmr-sit-abc123" "gsfc-eosdis-cmr-sit-abc123"

      "SIT without prefix already applied"
      "sit" "abc123" "gsfc-eosdis-cmr-sit-abc123"

      "UAT with prefix already applied"
      "uat" "gsfc-eosdis-cmr-uat-abc123" "gsfc-eosdis-cmr-uat-abc123"

      "UAT without prefix already applied"
      "uat" "abc123" "gsfc-eosdis-cmr-uat-abc123"

      "WL with prefix already applied"
      "wl" "gsfc-eosdis-cmr-wl-abc123" "gsfc-eosdis-cmr-wl-abc123"

      "WL without prefix already applied"
      "wl" "abc123" "gsfc-eosdis-cmr-wl-abc123"

      "OPS with prefix already applied"
      "ops" "gsfc-eosdis-cmr-ops-abc123" "gsfc-eosdis-cmr-ops-abc123"

      "OPS without prefix already applied"
      "ops" "abc123" "gsfc-eosdis-cmr-ops-abc123")))

(defn- queue-request-proxy
  "Creates a proxy for GetQueueUrlRequest that returns the given
  string for calls to getQueueUrl"
  [url-str]
  (proxy [GetQueueUrlResult]
         []
         (getQueueUrl [] url-str)))
                         
(defn- client-proxy
  "Creates a proxy for AWSSQSClient. The proxy returns a GetQueueUrlRequest
  proxy for calls to getQueueUrl. For calls to receiveMessages it calls the
  functions in receive-fns starting with the first function and then using 
  the next function in the list for each subsequent call until the list of
  receive functions is exhausted. At that point subsequent calls to 
  receiveMessages will throw an ExitException to force the calling thread to
  exit. This is done to prevent test worker threads from persisting after
  tests complete."
  [url-str receive-fn-index & receive-fns]
  (proxy [AmazonSQSClient]
         []
         (getQueueUrl [_q-name] (queue-request-proxy url-str))
         (receiveMessage [_req] (let [index (swap! receive-fn-index inc) 
                                      rec-fn (if (< index (count receive-fns))
                                                 (nth receive-fns index)
                                                 #(throw (ExitException.)))]
                                  (rec-fn)))))
  
(defn- wait-for-reads
  "Wait until the expected number of readMessage calls have occurred. If it takes
  longer than ms-to-wait give up."
  ([receive-fn-index exp-read-count]
   (wait-for-reads receive-fn-index exp-read-count 7000))
  ([receive-fn-index exp-read-count ms-to-wait]
   (let [start-time (System/currentTimeMillis)]
     (loop [read-count @receive-fn-index]
       (when (< read-count exp-read-count)
         (Thread/sleep 10)
         (when (< (- (System/currentTimeMillis) start-time) ms-to-wait)
           (recur @receive-fn-index)))))))

(deftest queue-worker-error-handling
  (testing "queue-workers-recover-from-errors"
    (let [receive-fn-index (atom -1)
          client (client-proxy "foo" 
                               receive-fn-index
                               ;; Throw an exception on the first call to getMessages
                               #(throw (Exception. "Receive failed!")) 
                               ;; Return an empty message on the next call to getMessages
                               (constantly (ReceiveMessageResult.)))
          broker (-> (sqs/create-queue-broker {})
                     (assoc :sqs-client client))]
      ;; Start a worker thread so we can test its error handling
      (#'sqs/create-async-handler broker "foo" identity)
      ;; Wait for the readMessage calls to complete
      (wait-for-reads receive-fn-index 2)
      ;; Verify the worker thread made it past the first exception to call receiveMessage
      ;; a second time.
      (is (= 2 @receive-fn-index)))))
