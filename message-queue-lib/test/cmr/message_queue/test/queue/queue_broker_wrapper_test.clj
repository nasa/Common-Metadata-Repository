(ns cmr.message-queue.test.queue.queue-broker-wrapper-test
  "Functions for testing the queue broker wrapper we use for testing."
  (:require
    [clojure.test :refer :all]
    [cmr.common.lifecycle :as lifecycle]
    [cmr.message-queue.queue.memory-queue :as memory-queue]
    [cmr.message-queue.queue.queue-protocol :as queue-protocol]
    [cmr.message-queue.test.queue-broker-wrapper :as queue-broker-wrapper]))

(def queue-name "test-queue")

(def queue-config
  "Message queue configuration for the test."
  {:queues [queue-name]
   :exchanges ["test-exchange"]
   :queues-to-exchanges {"test-queue" ["test-exchange"]}})

(defn- retry-handler
  "A handler that forces a retry"
  [& args]
  (throw (Exception. "force retry")))

(defn- get-number-of-retries
  "Returns the number of times a message was retried."
  [message message-history]
  (let [relevant-messages (->> message-history
                               (keep #(first (:messages %)))
                               (filter #(= (:name message) (:name %))))
        retry-counts (keep :retry-count relevant-messages)]
    (apply max 0 retry-counts)))

(defn- get-final-state
  "Returns the final state of a message."
  [message-history]
  (-> message-history last :action :message :state))

(deftest retries-exhausted-test
  (let [queue-broker (memory-queue/create-memory-queue-broker queue-config)
        queue-wrapper (lifecycle/start
                        (queue-broker-wrapper/create-queue-broker-wrapper queue-broker) nil)
        message {:name "test-retries"}]
    ;; Add a listener that will always return a failure when processing a message
    (queue-protocol/subscribe queue-wrapper queue-name retry-handler)
    (queue-protocol/publish-to-queue queue-wrapper queue-name message)
    (queue-broker-wrapper/wait-for-terminal-states queue-wrapper)
    (let [message-history (queue-broker-wrapper/get-message-queue-history queue-wrapper queue-name)
          number-of-retries (get-number-of-retries message message-history)
          final-state (get-final-state message-history)]
      (is (= 5 number-of-retries))
      (is (= :failure final-state)))
    (lifecycle/stop queue-wrapper nil)))
