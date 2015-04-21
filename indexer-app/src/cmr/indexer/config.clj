(ns cmr.indexer.config
  (:require [cmr.common.config :as cfg :refer [defconfig]]))

(defconfig index-queue-name
  "Queue used for requesting indexing of concepts"
  {:default "cmr_index.queue"})

(defconfig queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defconfig indexing-communication-method
  "Used to determine whether the indexer will expect index requests via http requests or
  via a message queue. Valid values are \"queue\", \"http\", and \"queue_with_fallback_to_http\""
  {:default "http"})

(defn use-index-queue?
  "Returns true if indexer is configured to use the message queue for indexing and false otherwise."
  []
  (case (indexing-communication-method)
    ("queue" "queue_with_fallback_to_http") true
    false))
