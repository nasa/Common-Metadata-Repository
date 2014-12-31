(ns cmr.index-queue.services.index-queue-service
  "Defines functions to handle adding indexing request messages from the queue"
  (:gen-class)
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
    				[cmr.common.config :as cfg]
        		[cmr.common.services.errors :as errors]
    				[cmr.index-queue.queue.index-queue :as iq]))

(defn index-concept
  "Places a request to index the given concept revision on the indexing queue"
  [context concept-id revision-id ignore-conflict?]
  )

