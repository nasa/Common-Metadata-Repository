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
  "Functions for working with a message queue"

  (create-queue
    [this queue-name]
    "Creates a queue with the given queue name")

  (publish
    [this queue-name msg]
    "Publishes a message on the queue with the given queue name")

  (subscribe
    [this queue-name handler-fn params]
    "Subscribes to the given queue using the given handler function with optional params.

    'handler-fn' is a function that takes a single parameter (the message) and attempts to
    process it. This function should respond with a map of the of the follwing form:
    {:status status :message message}
    where status is one of (:ok, :retry, :fail) and message is optional.
    :ok    - message was processed successfully
    :retry - message could not be processed and should be re-queued
    :fail  - the message cannot be processed and should not be re-queued")

  (message-count
    [this queue-name]
    "Returns the number of messages on the given queue")

  (reset
    [this]
    "Reset the broker, deleting any queues and any queued messages"))

;; A record that is used to start a consumer of messages on a queue.
(defrecord QueueListener
  [
   ;; Number of worker threads
   num-workers

   ;; A start-function starts some action, such as subscribing to a queue. It takes a context
   ;; that contains the system and can be used to perform the action.
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