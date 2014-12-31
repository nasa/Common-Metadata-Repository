(ns cmr.index-queue.queue.memory-queue
  "In-memory implementation of index-queue functionality"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
    				[cmr.common.config :as cfg]
        		[cmr.common.services.errors :as errors]
          	[cmr.index-queue.queue.index-queue :as index-queue]
            [cmr.index-queue.data.indexer :as indexer]))

(defrecord MemoryIndexQueue
  [
   ;; this implementation has no state - requests are handled immediately
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start [this system]
         this)

  (stop [this system]
        this)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
index-queue/IndexQueue

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-index-queue
  "Creates a simple in-memory index-queue"
  []
  (->MemoryIndexQueue))