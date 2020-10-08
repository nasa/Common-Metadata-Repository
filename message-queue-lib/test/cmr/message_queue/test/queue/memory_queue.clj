(ns cmr.message-queue.test.queue.memory-queue
  (:require
   [clojure.test :refer :all]
   [cmr.common.lifecycle :as l]
   [cmr.message-queue.queue.memory-queue :as mq]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]))

(defmacro is-wait
  "Works like is but will repeatedly run the test code until it passes or times out."
  [test-code]
  `(let [sleep-time# 50
         max-wait-time# 5000]
     (loop [num-tries# 0]
       (when (>= (* sleep-time# num-tries#) max-wait-time#)
         (is ~test-code)
         (throw (Exception. "Exceeded max wait time waiting for queue to acquiesce.")))

       (if ~test-code
         (is ~test-code)
         (do
           (Thread/sleep sleep-time#)
           (recur (inc num-tries#)))))))

(defn success-handler
  "A handler that doesn't do anything"
  [& args])
  ;; do nothing


(defn retry-handler
  "A handler that forces a retry"
  [& args]
  (throw (Exception. "force retry")))

(defn add-message-capturing-handler
  "Adds a handler to the message queue that will capture all messages that have been received and
  put them in an atom. Returns the atom."
  ([qb queue-name]
   (add-message-capturing-handler qb queue-name success-handler))
  ([qb queue-name resp-fn]
   (let [msgs-received (atom [])
         message-capturing-handler (fn [msg]
                                     (swap! msgs-received conj msg)
                                     (resp-fn))]
     (queue-protocol/subscribe qb queue-name message-capturing-handler)
     msgs-received)))

(deftest shutdown-test
  (let [make-qb #(l/start (mq/create-memory-queue-broker
                            {:queues ["a"]}) nil)]
    (testing "start multiple times"
      (let [qb (make-qb)]
        (is (= (l/start (l/start qb nil) nil) qb))))

    (testing "shutdown with no subscribers"
      (l/stop (make-qb) nil))

    (testing "shutdown with subscribers"
      (let [qb (make-qb)]
        (queue-protocol/subscribe qb "a" success-handler)
        (queue-protocol/subscribe qb "a" success-handler)
        (l/stop qb nil)))))

(deftest queue-success-test
  (let [qb (l/start (mq/create-memory-queue-broker
                      {:queues ["a"]}) nil)]
    (try
      (let [msgs-received (add-message-capturing-handler qb "a")
            messages [{:id 1} {:id 2} {:id 3}]]
        (doseq [msg messages]
          (is (queue-protocol/publish-to-queue qb "a" msg)))
        (is-wait (= messages @msgs-received)))
      (finally
        (l/stop qb nil)))))

(deftest queue-retry-test
  (let [qb (l/start (mq/create-memory-queue-broker
                      {:queues ["a"]}) nil)]
    (try
      (let [msgs-received (add-message-capturing-handler qb "a" retry-handler)]
        (is (queue-protocol/publish-to-queue qb "a" {:id 1}))
        (is-wait (= [{:id 1}
                     {:id 1 :retry-count 1}
                     {:id 1 :retry-count 2}
                     {:id 1 :retry-count 3}
                     {:id 1 :retry-count 4}
                     {:id 1 :retry-count 5}]
                    @msgs-received)))
      (finally
        (l/stop qb nil)))))

(deftest reset-test
  (let [qb (l/start (mq/create-memory-queue-broker
                      {:queues ["a"]}) nil)]
    (try
      (let [messages [{:id 1} {:id 2} {:id 3}]]
        (doseq [msg messages]
          (is (queue-protocol/publish-to-queue qb "a" msg)))
        (queue-protocol/reset qb)
        (let [msgs-received (add-message-capturing-handler qb "a")]
          (is (queue-protocol/publish-to-queue qb "a" {:id 4}))
          (is-wait (= [{:id 4}] @msgs-received))))
      (finally
        (l/stop qb nil)))))

(deftest exchange-success-test
  (let [qb (l/start (mq/create-memory-queue-broker
                      {:queues ["a" "b"]
                       :exchanges ["e"]
                       :queues-to-exchanges {"a" ["e"] "b" ["e"]}}) nil)]
    (try
      (let [a-msgs-received (add-message-capturing-handler qb "a")
            b-msgs-received (add-message-capturing-handler qb "b")
            messages [{:id 1} {:id 2} {:id 3}]]
        (doseq [msg messages]
          (is (queue-protocol/publish-to-exchange qb "e" msg)))
        (is-wait (= messages @a-msgs-received))
        (is-wait (= messages @b-msgs-received)))
      (finally
        (l/stop qb nil)))))
