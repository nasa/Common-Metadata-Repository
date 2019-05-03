(ns cmr.message-queue.test.queue.sqs
  (:require
   [clojure.test :refer :all]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.test.test-util :refer [with-env-vars]]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.queue.sqs :as sqs]
   [cmr.message-queue.test.queue-broker-wrapper :as broker-wrapper])
  (:import
   (cmr.message_queue.test ExitException)
   (com.amazonaws.services.sqs AmazonSQSClient)
   (com.amazonaws.services.sqs.model GetQueueUrlResult)
   (com.amazonaws.services.sqs.model ReceiveMessageResult)))

(def ^:private get-test-queue-type
  "Get the system queue type. This is used by this test to determine which
  backend to use.

  Note that this particular approach is used due to the fact that the
  dev-system may not be available to the `cmr.message-queue` library
  at the time the function is called. If that's the case, the old, original
  behaviour is selected."
  (or (ns-resolve (create-ns 'cmr.dev-system.config) 'dev-system-queue-type)
      (constantly "local")))

(defconfig testing-queue-name
  "This is used to set the queue name for tests, since:
  1) we need to reference the queue name in a global scope while simultaneously
     in the context of a particular proxy instantiation, and
  2) adding methods to a proxy is not permitted."
  {:default ""
   :type String})

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

(defn- recv-fn
  "Returns a function to call on receive messages."
  [receive-fns idx]
  (if (< idx (count receive-fns))
    (nth receive-fns idx)
    #(throw (ExitException.))))

(defn- fake-client-proxy
  "Creates a proxy for AWSSQSClient. This proxy does the following:

  * Returns a `GetQueueUrlRequest` proxy for calls to getQueueUrl.
  * For calls to `receiveMessages`, it calls the functions in receive-fns
    starting with the first function and then using the next function in the
    list for each subsequent call until the list of receive functions is
    exhausted. At that point subsequent calls to receiveMessages will throw an
    ExitException to force the calling thread to exit. This is done to prevent
    test worker threads from persisting after tests complete."
  [url-str receive-fn-index & receive-fns]
  (set-testing-queue-name! "foo")
  (proxy [AmazonSQSClient]
         []
         (getQueueUrl [_q-name] (queue-request-proxy url-str))
         (receiveMessage [_req] (let [idx (swap! receive-fn-index inc)]
                                  ((recv-fn receive-fns idx))))))

(defn- local-client-proxy
  "Creates a proxy for AWSSQSClient. For calls to `receiveMessages` it calls
  the functions in receive-fns starting with the first function and then using
  the next function in the list for each subsequent call until the list of
  receive functions is exhausted. At that point subsequent calls to
  receiveMessages will throw an ExitException to force the calling thread to
  exit. This is done to prevent test worker threads from persisting after
  tests complete."
  [receive-fn-index & receive-fns]
  (set-testing-queue-name! "gsfc-eosdis-cmr-local-ingest_queue")
  (proxy [AmazonSQSClient]
         []
         (receiveMessage [_req] (let [idx (swap! receive-fn-index inc)]
                                  ((recv-fn receive-fns idx))))))

(defn- proxy-constructor
  "Creates a client proxy.

  If the queue type configuration has been set to AWS (e.g., by setting the
  `CMR_QUEUE_TYPE` env variable), then an actual SQS endpoint should be used,
  in which case the Java client will either user the default (and attempt to
  connect to Amazon's endpoints) or it will use the endpoint defined by the
  `CMR_SQS_ENDPOINT` env variable.

  Otherwise, a mocked proxy will be used."
  [receive-fn-index]
  (let [endpoint (sqs/sqs-endpoint)
        ex-fn #(throw (Exception. "Receive failed!"))
        next-get-msgs-fn (constantly (ReceiveMessageResult.))]
    (if (= :aws (get-test-queue-type))
      (let [client (local-client-proxy receive-fn-index ex-fn next-get-msgs-fn)]
        (sqs/configure-aws-client
         client
         :setEndpoint endpoint))
      (fake-client-proxy
       "foo"
       receive-fn-index
       ;; Throw an exception on the first call to getMessages
       ex-fn
       ;; Return an empty message on the next call to getMessages
       next-get-msgs-fn))))

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
          client (proxy-constructor receive-fn-index)
          broker (-> (sqs/create-queue-broker {})
                     (assoc :sqs-client client))]
      ;; Start a worker thread so we can test its error handling
      (#'sqs/create-async-handler
       broker (testing-queue-name) identity false)
      ;; Wait for the readMessage calls to complete
      (wait-for-reads receive-fn-index 2)
      ;; Verify the worker thread made it past the first exception to call receiveMessage
      ;; a second time.
      (is (= 2 @receive-fn-index)))))
