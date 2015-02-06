(ns cmr.indexer.config
  (:require [cmr.common.config :as cfg]))

(def index-queue-name
  "Queue used for requesting indexing of concepts"
  (cfg/config-value-fn :index-queue-name "cmr_index.queue"))

(def queue-listener-count
  "Number of worker threads to use for the queue listener"
  (cfg/config-value-fn :queue-listener-count "5" #(Integer. %)))

(def indexing-communication-method
  "Either \"http\" or \"queue\""
  (cfg/config-value-fn :indexing-communication-method "http"))

(def use-index-queue?
  "Boolean flag indicating whether or not to use the message queue for indexing"
  #(= "queue" (indexing-communication-method)))
