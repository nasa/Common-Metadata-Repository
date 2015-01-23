(ns cmr.message-queue.queue.memory-queue
  "In-memory implementation of index-queue functionality"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.config :as cfg]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.config :as config]
            [cheshire.core :as json]
            [cmr.message-queue.services.queue :as queue])
  (:import java.util.concurrent.LinkedBlockingQueue))

(defn- push-message
  "Pushes a message on the queue whether it is running or not."
  [mem-queue queue-name msg]
  (let [queue (get @(:queue-atom) queue-name)]
    (future (.put queue msg))))

(defn- push-message-with-validation
  "Validates that the queue is running, then push a message onto it. If the queue
  is not running, throws an exception."
  [mem-queue msg]
  (when-not @(:running-atom mem-queue)
    (errors/internal-error! "Queue is down!"))
  (push-message mem-queue msg))

(defn- start-consumer
  "Repeatedly pulls messages off the queue and calls callbacks"
  [memory-queue queue-name handler params]
  (info "Starting consumer")
  (while @(:running-atom memory-queue)
    ;; Use a blocking read
    (debug "WATING FOR DATA...")
    (let [queue @(:queue-atom memory-queue)
          msg (.take queue)
          _ (debug "GOT DATA" msg)]
      (try
        (when-not (= (:action msg) "quit")
          ;; Do do something
          )
        (catch Throwable e
          (error (.getMessage e))
          ;; Requeue the message
          (push-message msg)))))
  (info "Stopping consumer"))

(defrecord MemoryIndexQueue
  [
   ;; maxiumum number of elements in the queue
   queue-capacity

   ;; holds a map of names to Java BlockingQueue instances
   queue-atom

   ;; number of subscribers/workers
   num-subscribers

   ;; queues that must be created on startup
   required-queues

   ;; Atom holding true or false to indicate it's running
   ;; This needs to be an atom so we can set it to false so our worker threads will terminate.
   running-atom
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting memory queue")
    (when @(:running-atom this)
      (errors/internal-error! "Queue is already running"))
    (swap! (:queue-atom this) (fn [_] (new java.util.concurrent.LinkedBlockingQueue
                                           (:queue-capacity this))))
    (swap! (:running-atom this) (fn [_] true))
    (doseq [queue-name (:required-queues this)]
        (queue/create-queue this queue-name))
      (debug "Required queues created")
    this)

  (stop
    [this system]
    (when @(:running-atom this)
      (info "Changing state to stopped")
      (swap! (:running-atom this) (fn [_] false))
      (info "Stopping consumers")
      (dorun
        (repeatedly (:num-subscribers this)
                    (fn []
                      (push-message this {:action "quit"}))))
      this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
queue/Queue

  (create-queue
    [this queue-name]
    (let [queue-atom (:queue-atom this)
      ;; create the queue
      _ (info "Creating queue" queue-name)
      queue (new java.util.concurrent.LinkedBlockingQueue
                                           (:queue-capacity this))]
      (swap! queue-atom (fn [queue-map]
                          (assoc queue-map queue-name queue)))))


  (publish
    [this queue-name msg]
    (debug "publishing msg:" msg " to queue:" queue-name)
    )

  (subscribe
    [this queue-name handler params]
    (start-consumer this queue-name handler params))

  (message-count
    [this queue-name]
    )

  (purge-queue
    [this queue-name]
    )

  (delete-queue
    [this queue-name]
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-queue
  "Creates a simple in-memory queue"
  [capacity num-workers required-queues]
  (->MemoryIndexQueue capacity (atom nil) num-workers required-queues (atom false)))

