(ns cmr.system-int-test.utils.queue
  "Functions to support testing while using the message queue"
  (:require [cmr.indexer.config :as config]
            [cmr.common.log :refer (debug)]
            [clj-http.client :as client]
            [cheshire.core :as json]))

(defn- queue-message-count
  "Use the RabbitMQ API to get the count of all messages (ready or unacked) for the given queue
  and its associated wait queues"
  [_queue-name]
  (let [status (-> (client/get "http://localhost:15672/api/queues/%2f"
                               {:basic-auth ["cmr" "cmr"]})
                   :body
                   (json/decode true))]
    (reduce (fn [total, queue-status]
              (let [{:keys [name messages]} queue-status]
                (if (re-find #"cmr_index*" name)
                  (+ total messages)
                  total)))
            0
            status)))

(defn- wait-for-queue
  "Repeatedly checks to see if the given queue is empty, sleeping in between checks"
  [_queue-broker _queue-name]
  (Thread/sleep 2000)
  (loop [msg-count (queue-message-count "cmr_index")]
    (when (pos? msg-count)
      (debug "Queue has" msg-count " messages")
      (Thread/sleep 100)
      (recur (queue-message-count "cmr_index")))))

(defn wait-for-index-queue
  "Wait until the index queue is empty"
  []
  (debug "Waiting for index queue")
  (let [s (-> 'user/system find-var var-get)
        queue-broker (get-in s [:apps :indexer :queue-broker])
        queue-name (config/index-queue-name)]
    (wait-for-queue queue-broker queue-name)
    (debug "index queue is empty")))
