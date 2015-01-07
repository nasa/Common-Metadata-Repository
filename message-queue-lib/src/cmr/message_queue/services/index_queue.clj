(ns cmr.message-queue.services.index-queue
  "Declares a protocol for publishing to a queue for concept indexing services"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
    				[cmr.common.config :as cfg]
        		[cmr.common.services.errors :as errors]))

(defprotocol IndexQueue
  "Functions for adding indexing/deletion messages to a message queue"

  (index-concept
    [context concept-id revision-id]
    "Index the given concpet revision")

  (delete-concept-from-index
    [context concept-id revision-id]
    "Remove the given concept revision")

  (delete-provider-from-index
    [context provider-id]
    "Remove a provider and all its concepts from the index")

  (reindex-provider-collections
    [context provider-id]
    "Reindexes all the concepts for the given provider"))