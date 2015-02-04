(ns cmr.message-queue.services.queue
  "Declares a protocol for creating and interacting with queues and a record that defines
  a queue listener"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]))

(defn retry-limit-met?
  "Determine whether or not the given message has met (or exceeded)
   the number of allowed retries"
  [msg retry-limit]
  (when-let [retry-count (:retry-count msg)]
    (>= retry-count retry-limit)))

(defprotocol Queue
  "Functions for adding messages to a message queue"

  (create-queue
    [this queue-name]
    "Creates a queue with the given parameter map")

   (publish
    [this queue-name msg]
    "Publishes a message on the queue")

  (subscribe
    [this queue-name handler params]
    "Subscribes to the given queue using the given handler with optional params.
    The handler must attempt to process the message and respond with one of the following:
      :ok    - message was processed successfully
      :retry - message could not be processed and should be re-queued
      :fail  - the message cannot be processed and should not be re-queued")

  (message-count
    [this queue-name]
    "Returns the number of messages on the given queue")

  (reset
    [this]
    "Reset the broker, deleting any queues and any queued messages"))

(defrecord QueueListener
  [
   ;; Number of worker threads
   num-workers

   ;; function to call to start a listener - takes a context as its sole argument
   start-function

   ;; true or false to indicate it's running
   running?
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting queue listeners")
    (when (:running? this)
      (errors/internal-error! "Already running"))
    (let [{:keys [num-workers start-function]} this]
      (doseq [n (range 0 num-workers)]
        (start-function {:system system})))
    (assoc this :running? true))

  (stop
    [this system]
    (assoc this :running? false)))

(defn create-queue-listener
  "Create a QueueListener"
  [params]
  (let [{:keys [num-workers start-function]} params]
    (->QueueListener num-workers start-function false)))