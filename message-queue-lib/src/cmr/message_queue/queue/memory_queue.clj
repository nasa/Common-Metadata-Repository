(ns cmr.message-queue.queue.memory-queue
  "In-memory implementation of index-queue functionality"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.config :as cfg]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.index-queue :as index-queue]
            [cmr.message-queue.data.indexer :as indexer]
            [cheshire.core :as json])
  (:import java.util.concurrent.LinkedBlockingQueue))

(defn- push-message
  "Push a message on the queue"
  [mem-queue msg]
  (when-not (:running? mem-queue)
    (errors/internal-error! "Queue is down!"))
  (let [queue @(:queue-atom mem-queue)]
    (future (.put queue msg))))

(defn- start-consumer
  "Repeatedly pulls messages off the queue and calls callbacks"
  [memory-queue]
  (info "Starting consumer")
  (while @(:running-atom memory-queue)
    ;; Use a blocking read
    (debug "WATING FOR DATA...")
    (let [queue @(:queue-atom memory-queue)
          msg (.take queue)
          _ (debug "GOT DATA" msg)]
      (try
        (indexer/handle-indexing-request (:action msg) msg)
        (catch Throwable e
          (error (.getMessage e))
          ;; Requeue the message
          (push-message msg)))))
  (info "Stopping consumer"))

(defrecord MemoryIndexQueue
  [
   ;; maxiumum number of elements in the queue
   queue-capacity

   ;; holds a Java BlockingQueue instance
   queue-atom

   ;; number of subscribers/workers
   num-subscribers

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
    (dorun
      (repeatedly (:num-subscribers this)
                  (fn []
                    ;; Start threads running the queue listeners
                    (future (start-consumer this)))))
    (assoc this :running? true :paused? false))

  (stop
    [this system]
    (when @(:running-atom this)
      (debug "Changing state to stopped")
      (swap! (:running-atom this) (fn [_] false))
      (debug "Removing queue")
      #_(swap! (:queue-atom this) nil)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  index-queue/IndexQueue

  (index-concept
    [this concept-id revision-id]
    (let [msg {:concept-id concept-id
               :revision-id revision-id
               :action "index-concept"}
          payload (json/encode msg)]
      (push-message this msg)))

  (delete-concept-from-index
    [this concept-id revision-id]
    (let [context {}]
      (future (indexer/delete-concept-from-index context concept-id revision-id))))

  (delete-provider-from-index
    [this provider-id]
    (let [context {}]
      (future (indexer/delete-provider-from-index context provider-id))))

  (reindex-provider-collections
    [this provider-id]
    (let [context {}]
      (future (indexer/reindex-provider-collections context provider-id)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-queue
  "Creates a simple in-memory index-queue"
  [capacity num-workers]
  (->MemoryIndexQueue capacity (atom nil) num-workers (atom false)))

(comment
  (def q (atom (create-queue 25 4)))

  (swap! q #(lifecycle/start % {}))
  (index-queue/index-concept @q "G1000-PROV1" 1)

  (swap! q #(lifecycle/stop % {}))

)
