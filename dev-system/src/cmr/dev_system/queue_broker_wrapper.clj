(ns cmr.dev-system.queue-broker-wrapper
  "Functions to wrap the message queue while testing. The wrapper is necessary because messages
  are processed asynchronously, but for our tests we will often want to wait until messages are
  processed before performing other steps or confirming results. It keeps track, in memory, of
  every message sent to the message queue. It has the ability to wait until each one of these
  messages has been processed. For this to work we have to use the same queue broker wrapper
  instance on the sender and receiver. This means they both need to be in the same JVM instance."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.message-queue.services.queue :as queue]
            [cmr.message-queue.config :as iconfig]
            [cmr.common.log :as log :refer (debug info warn error)]
            [clojure.set :as set]))

(def message-id-key
  "Key used to track messages within a message map. Each message will have a unique id."
  (keyword (str (ns-name *ns*) "-id")))

(defn- set-message-state
  "Set the state of a message on the queue"
  [broker-wrapper msg state]
  (let [message-state-atom (:message-state-atom broker-wrapper)
        message-id (message-id-key msg)]
    (swap! message-state-atom #(assoc % message-id state))))

(defn handler-wrapper
  "Wraps handler function to count acks, retries, fails"
  [broker-wrapper handler]
  (fn [context msg]
    (if (-> broker-wrapper :resetting?-atom deref)
      (do
        (set-message-state broker-wrapper msg :failed)
        {:status :fail :message "Forced failure on reset"})
      (let [resp (handler context msg)]
        (case (:status resp)
          :ok (set-message-state broker-wrapper msg :processed)

          :retry (when (queue/retry-limit-met? msg (count (iconfig/rabbit-mq-ttls)))
                   (set-message-state broker-wrapper msg :failed))

          ;; treat nacks as acks for counting purposes
          :fail (set-message-state broker-wrapper msg :failed)

          (throw (Exception. (str "Invalid response: " (pr-str resp)))))
        resp))))

(defn- current-message-states
  "Return the set of all the unique states of the messages currently held by the wrapper."
  [broker-wrapper]
  (let [message-map @(:message-state-atom broker-wrapper)]
    (-> message-map vals set)))

(defn- wait-for-states
  "Wait until the messages that have been enqueued have all reached one of the given
  states."
  [broker-wrapper success-states]
  (let [succ-states-set (set success-states)]
    (loop [current-states (current-message-states broker-wrapper)]
      (let [diff-states (set/difference current-states succ-states-set)]
        ;; The current states should only consist of the success states and possibly nil;
        ;; anything else indicates a failure.
        (when-not (empty? diff-states)
          (when-not (= #{nil} diff-states)
            (throw (Exception. (str "Unexpected final message state(s): " diff-states))))
          (Thread/sleep 100)
          (recur (current-message-states broker-wrapper)))))))

(defrecord BrokerWrapper
  [
   ;; The broker that does the actual work
   queue-broker

   ;; Atom holding the map of message ids to states
   message-state-atom

   ;; Sequence generator for internal message ids. These ids are used to uniquely identify every
   ;; message that comes through the wrapper.
   id-sequence-atom

   ;; Atom holding the resetting boolean flag. This flag is set to true to indicate that the wrapper
   ;; is in process of being reset, and any messages processed by the wrapper should result in
   ;; a :fail response. This indirectly allows the wrapper to clear the queue and prevent retries.
   ;; A value of false indicates normal operation.
   resetting?-atom

   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting queue-broker wrapper")
    (update-in this [:queue-broker] #(lifecycle/start % system)))

  (stop
    [this system]
    (update-in this [:queue-broker] #(lifecycle/stop % system)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue/Queue

  (create-queue
    [this queue-name]
    ;; defer to wrapped broker
    (queue/create-queue queue-broker queue-name))

  (publish
    [this queue-name msg]
    ;; record the message
    (let [msg-id (swap! id-sequence-atom inc)
          tagged-msg (assoc msg message-id-key msg-id)]
      (swap! message-state-atom #(assoc % msg-id nil))
      ;; delegate the request to the wrapped broker
      (queue/publish queue-broker queue-name tagged-msg)))

  (subscribe
    [this queue-name handler params]
    (queue/subscribe queue-broker queue-name handler params))

  (message-count
    [this queue-name]
    (let [qcount (queue/message-count queue-broker queue-name)
          unprocessed-count (count (filter (complement :processed) @message-state-atom))]
      (when (not= qcount unprocessed-count)
        (warn (format "Message count [%d] for Rabbit MQ did not match internal count [%d]"
                      qcount
                      unprocessed-count)))
      qcount))

  (reset
    [this]
    (reset! (:resetting?-atom this) true)
    (try
      (wait-for-states this [:processed :failed])
      (queue/reset queue-broker)
      (reset! message-state-atom {})
      (reset! id-sequence-atom 0)
      (finally
        (reset! resetting?-atom false)))))

(defn wait-for-indexing
  "Wait for all messages to be marked as processed"
  [broker-wrapper]
  (wait-for-states broker-wrapper [:processed]))

(defn create-queue-broker-wrapper
  "Create a BrokerWrapper"
  [broker]
  (->BrokerWrapper broker (atom {}) (atom 0) (atom false)))
