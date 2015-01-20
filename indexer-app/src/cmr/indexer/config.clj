(ns cmr.indexer.config
  (:require [cmr.common.config :as cfg]))

(def index-queue-name
  "Queue used for requesting indexing of concepts"
  (cfg/config-value-fn :index-queue-name "cmr_index.queue"))
