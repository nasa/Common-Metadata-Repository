(ns cmr.system-int-test.utils.queue
  "Functions to support testing while using the message queue"
  (:require [cmr.message-queue.services.queue :as queue]
            [cmr.indexer.config :as config]
            [cmr.common.log :as log :refer (debug info warn error)]))

(defn- wait-for-queue
  "Repeatedly checks to see if the given queue is empty, sleeping in between checks"
  [queue-broker queue-name]
  (while (> (queue/message-count queue-broker queue-name) 0)
    (Thread/sleep 50))
  (Thread/sleep 1000))

(defn wait-for-index-queue
  "Wait until the index queue is empty"
  []
  (debug "Waiting for index queue")
  (let [s (-> 'user/system find-var var-get)
        _ (debug s)
        queue-broker (get-in s [:apps :indexer :queue-broker])
        queue-name (config/index-queue-name)]
    (wait-for-queue queue-broker queue-name)
    (debug "index queue is empty")))

