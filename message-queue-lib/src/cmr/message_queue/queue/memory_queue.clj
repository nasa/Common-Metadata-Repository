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

(defn- attempt-retry
  "Retry a message on the queue if the maximum number of retries has not been exceeded"
  [queue msg resp]
  (let [repeat-count (get msg :repeat-count 0)]
    (if (> (inc repeat-count) (config/rabbit-mq-max-retries))
      (debug "Max retries exceeded for procesessing message:" msg)
      (let [msg (assoc msg :repeat-count (inc repeat-count))]
        (debug "Message" msg "requeued with response:" (:message resp))
        ;; We don't use any delay before requeueing with the in-memory queues
        (.put queue msg)))))

(defn- start-consumer
  "Repeatedly pulls messages off the queue and calls a callback with each message"
  [queue-broker queue-name handler]
  (debug "Starting consumer for queue" queue-name)
  (let [queue-map-atom (:queue-map-atom queue-broker)
        queue (get queue-map-atom queue-name)]
    (loop [msg (.take queue)]
      (let [action (:action msg)]
        (if (= :quit action)
          (info "Quitting consumer for queue" queue-name)
          (do (try
                (let [resp (handler msg)]
                  (case (:status resp)
                    :ok (debug "Message" msg "processed successfully")

                    :retry (attempt-retry queue msg resp)

                    :fail
                    ;; bad data - nack it
                    (debug "Rejecting bad data:" (:message resp))))
                (catch Exception e
                  (error "Message processing failed for message" msg "with error:"
                         (.gettMessage e))
                  (attempt-retry queue msg {:message (.gettMessage e)})))
            (recur ((.take queue)))))))
    ;; increment the listener count for the queue
    (swap! queue-map-atom (fn [queue-map]
                            (update-in queue-map [queue-name 1] inc)))))

(defn- named-queue
  "Get the queue entry [queue num-listeners] (if any) for the qiven name"
  [queue-broker queue-name]
  (let [queue-map @(:queue-map-atom queue-broker)]
    (get queue-map queue-name)))


(defrecord MemoryQueueBroker
  [
   ;; maxiumum number of elements in the queue
   queue-capacity

   ;; holds a map of names to Java BlockingQueue instances
   queue-map-atom

   ;; queues that must be created on startup
   required-queues

   ;; Flagin indicating running or not
   running?
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting memory queue")
    (when (:running-atom this)
      (errors/internal-error! "Queue is already running"))
    (swap! (:queue-map-atom this) (fn [_] {}))
    (let [this (assoc this :running? true)]
      (doseq [queue-name (:required-queues this)]
        (queue/create-queue this queue-name))
      (debug "Required queues created")
      this))

  (stop
    [this system]
    (when (:running? this)
      (info "Stopping memory queue and removing all queues")
      (doseq [queue-name (keys @(:queue-map-atom this))]
        (queue/delete-queue this queue-name))
      (assoc this :running? false)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue/Queue

  (create-queue
    [this queue-name]
    (let [queue-map-atom (:queue-map-atom this)
          ;; create the queue
          _ (info "Creating queue" queue-name)
          queue (new java.util.concurrent.LinkedBlockingQueue
                     (:queue-capacity this))]
      (swap! queue-map-atom (fn [queue-map]
                              ;; add an entry for the queue and its starting listner count (0)
                              (assoc queue-map queue-name [queue 0])))))


  (publish
    [this queue-name msg]
    (debug "publishing msg:" msg " to queue:" queue-name)
    (when-not (:running? this)
      (errors/internal-error! "Queue is not running."))
    (let [queue (named-queue this queue-name)]
      (.put queue msg)))

  (subscribe
    [this queue-name handler params]
    (start-consumer this queue-name handler params))

  (message-count
    [this queue-name]
    (let [[queue _] (named-queue this queue-name)]
      (.size queue)))

  (purge-queue
    [this queue-name]
    (let [[queue _](named-queue this queue-name)]
      (.clear queue)))

  (delete-queue
    [this queue-name]
    ;; get a reference to the queue so we can access it even after we remove it from the map
    (let [queue-map-atom (:queue-map-atom this)
          [queue num-listeners] (named-queue this queue-name)]
      ;; remove it from the map so no new consumers will start listening to it
      (swap! queue-map-atom (fn [queue-map] (dissoc queue-map queue-name)))
      ;; now close all the listeners - TODO fix race condition of consumer getting added
      ;; after we read num-listeners above
      (doseq [n (range 0 num-listeners)]
        (.put queue {:action :quit}))))
  )

    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


    (defn create-queue-broker
      "Creates a simple in-memory queue broker"
      [capacity required-queues]
      (->MemoryQueueBroker capacity (atom nil) required-queues false))

