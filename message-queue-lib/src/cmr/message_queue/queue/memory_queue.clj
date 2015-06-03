(ns cmr.message-queue.queue.memory-queue
  "TODO"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.message-queue.config :as config]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.queue :as queue]))


(defrecord MemoryQueue
  [
   ;; A list of queue names
   queues

   ;; A list of exchange names
   exchanges

   ;; A map of queue name to exchange names. Messages sent to an exchange are broadcast to all
   ;; queues bound to the exchange.
   bindings

   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]

      this)

  (stop
    [this system]

    this
    )
  )


(defn create-memory-queue
  []
  (->MemoryQueue))