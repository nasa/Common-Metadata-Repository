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
  via a message queue.  Valid values are \"queue\" and \"http\""
  {:default "http"})

(def use-index-queue?
  "Boolean flag indicating whether or not to use the message queue for indexing"
  #(= "queue" (indexing-communication-method)))
