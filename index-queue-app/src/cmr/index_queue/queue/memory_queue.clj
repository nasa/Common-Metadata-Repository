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
   ;; This implementation has no state - asynchronous calling is handled with futures
   ;; to simulate a queue.
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
    (future (indexer/index-concept context concept-id revision-id)))

  (delete-concept-from-index
    [context concept-id revision-id]
    (future (indexer/delete-concept-from-index context concept-id revision-id)))

  (delete-provider-from-index
    [context provider-id]
    (future (indexer/delete-provider-from-index context provider-id)))

  (reindex-provider-collections
    [context provider-id]
    (future (indexer/reindex-provider-collections context provider-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-index-queue
  "Creates a simple in-memory index-queue"
  []
  (->MemoryIndexQueue))