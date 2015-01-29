(ns cmr.indexer.config
  (:require [cmr.common.config :as cfg]))

(def index-queue-name
  "Queue used for requesting indexing of concepts"
  (cfg/config-value-fn :index-queue-name "cmr_index.queue"))

(def use-index-queue?
  "Boolean flag indicating whether or not to use the message queue for indexing"
  (cfg/config-value-fn :use-index-queue? "false" #(boolean (Boolean. ^String %))))

