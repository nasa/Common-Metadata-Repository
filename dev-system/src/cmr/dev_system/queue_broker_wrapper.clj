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
            [clojure.set :as set]
            [cmr.common.util :as util]))

(def ^:const ^:private valid-action-types
  "A set of the valid action types for a message queue:
  :enqueue - Message has been added to the queue.
  :process - Message has been processed."
  #{:enqueue
    :process})

(defn- append-to-message-queue-history
  "Create a message queue history map and append it to the current message queue history.

  Parameters:
  message-queue-history-value - current value of the message-queue-history-atom
  action-type - A keyword representing the action taken on the message queue.
  message - Map with :id, :action (either \"index-concept\" or \"delete-concept\"), :concept-id, and
  :revision-id. message can be nil for messages unrelated to a concept.
  resulting-state - One of the valid message states

  Example message-queue-history-map:
  {:action {:action-type :enqueue
  :message {:id 1 :concept-id \"C1-PROV1\" :revision-id 1 :state :initial}}
  :messages [{:id 1 :concept-id \"C1-PROV1\" :revision-id 1 :state :initial}]}"
  [message-queue-history-value action-type message resulting-state]
  {:pre [(valid-action-types action-type) message]}
  (let [message-with-state (assoc message :state resulting-state)
        messages (conj
                   ;; Messages are unique based on id - if the action is to change the state
                   ;; of a message we already know about, we replace the original message
                   ;; with the new one in our list of messages.
                   (remove #(= (:id message) (:id %))
                           (:messages (last message-queue-history-value)))
                   message-with-state)
        new-action (util/remove-nil-keys {:action-type action-type
                                          :message message-with-state})]
    (conj message-queue-history-value {:action new-action :messages messages})))

(defn- update-message-queue-history
  "Called when an event occurs on the message queue in order to add a new entry to the message
  queue history. See append-to-message-queue-history for a description of the parameters."
  [broker-wrapper action-type data resulting-state]
  (swap! (:message-queue-history-atom broker-wrapper)
         append-to-message-queue-history action-type data resulting-state))

(comment
  (update-message-queue-history (create-queue-broker-wrapper nil)
                                :enqueue {:concept-id "C1-PROV1" :revision-id 1 :id 1} :initial))

(def ^:const ^:private valid-message-states
  "Set of valid message states:
  :initial - message first created
  :retry - the message failed processing and is currently retrying
  :failed - the message failed processing and all retries have been exhausted
  :processed - the message has completed successfully"
  #{:initial
    :retry
    :failure
    :success})

(def ^:const ^:private terminal-states
  "Subset of valid message states which are considered final. Used to determine when a message will
  no longer be processed."
  #{:failure
    :success})

(defn- current-message-states
  "Return a sequence of message states for all messages currently held by the wrapper."
  [broker-wrapper]
  (->> @(:message-queue-history-atom broker-wrapper)
       last
       :messages
       (map :state)))

(defn- current-messages
  "Return a list of the current messages."
  [broker-wrapper]
  (->> @(:message-queue-history-atom broker-wrapper)
       last
       :messages))

(defn- wait-for-terminal-states
  "Wait until the messages that have been enqueued have all reached a terminal states. If it takes
  longer than ms-to-wait, log a warning and stop waiting."
  ([broker-wrapper]
   (wait-for-terminal-states broker-wrapper 7000))
  ([broker-wrapper ms-to-wait]
   (let [start-time (System/currentTimeMillis)]
     (loop [current-states (set (current-message-states broker-wrapper))]
       (when (seq (set/difference current-states terminal-states))
         (Thread/sleep 10)
         (if (< (- (System/currentTimeMillis) start-time) ms-to-wait)
           (recur (set (current-message-states broker-wrapper)))
           (warn (format "Waited %d ms for messages to complete, but they did not complete: %s"
                         ms-to-wait
                         (current-messages broker-wrapper)))))))))

(defrecord BrokerWrapper
  [
   ;; The broker that does the actual work
   queue-broker

   ;; Sequence generator for internal message ids. These ids are used to uniquely identify every
   ;; message that comes through the wrapper.
   id-sequence-atom

   ;; Atom holding the resetting boolean flag. This flag is set to true to indicate that the wrapper
   ;; is in process of being reset, and any messages processed by the wrapper should result in
   ;; a :fail response. This indirectly allows the wrapper to clear the queue and prevent retries.
   ;; A value of false indicates normal operation.
   resetting?-atom

   ;; Contains a history of the message queue represented as a vector of message queue state maps.
   ;; Each message queue state map contains an :action map indicating the action that caused the
   ;; message queue to change state and a :messages sequence of the messages in the queue."
   message-queue-history-atom

   ;; Number of times every request should return an error response indicating retry prior to being
   ;; processed normally. Useful for automated tests verifying specific behaviors on retry and
   ;; failure.
   num-retries-atom

   ;; Tracks whether enqueuing messages will fail with a timeout error.
   timeout-atom
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
          tagged-msg (assoc msg :id msg-id)]

      ;; Set the initial state of the message to :initial
      (update-message-queue-history this :enqueue tagged-msg :initial)

      ;; Mark the enqueue as failed if we are timing things out or it fails
      (if (or @timeout-atom (not (queue/publish queue-broker queue-name tagged-msg)))
        (do
          (update-message-queue-history this :enqueue tagged-msg :failure)
          false)
        true)))

  (subscribe
    [this queue-name handler params]
    (queue/subscribe queue-broker queue-name handler params))

  (message-count
    [this queue-name]
    (let [qcount (queue/message-count queue-broker queue-name)
          unprocessed-count (count (remove :processed (current-message-states)))]
      (when (not= qcount unprocessed-count)
        (warn (format "Message count [%d] for Rabbit MQ did not match internal count [%d]"
                      qcount
                      unprocessed-count)))
      qcount))

  (reset
    [this]
    (reset! (:resetting?-atom this) true)
    (try
      (wait-for-terminal-states this)
      (queue/reset queue-broker)
      (reset! id-sequence-atom 0)
      (reset! message-queue-history-atom [])
      (finally
        (reset! resetting?-atom false))))

  (health
    [this]
    (queue/health (:queue-broker this))))

(defn wait-for-indexing
  "Wait for all messages to be marked as processed or failed."
  [broker-wrapper]
  (wait-for-terminal-states broker-wrapper))

(defn get-message-queue-history
  "Returns the message-queue-history."
  [broker-wrapper]
  @(:message-queue-history-atom broker-wrapper))

(defn set-message-queue-retry-behavior!
  "Used to change the behavior of the message queue to indicate that each message should fail
  with a retry response code a certain number of times prior to succeeding. If the num-retries
  is set to 0 every message will be processed normally. If num-retries is set higher than the
  maximum allowed retries the message will end up being marked as failed once the retries have
  been exhausted."
  [broker-wrapper num-retries]
  ;; Use an atom to set state?
  (reset! (:num-retries-atom broker-wrapper) num-retries))

(defn set-message-queue-timeout-expected!
  "Used to change the behavior of the message queue to indicate that enqueueing a message will
  result in a timeout."
  [broker-wrapper timeout?]
  ;; Use an atom to set state?
  (reset! (:timeout-atom broker-wrapper) timeout?))

(defn- queue-response->message-state
  "Converts the response of a message queue action to the appropriate message state. If a message
  has a retry status and has exceeded the max retries then return failure."
  [response msg]
  {:pre [(valid-message-states (:status response))]}
  (let [{:keys [:status]} response]
    (if (and (= :retry status)
             (queue/retry-limit-met? msg (count (iconfig/rabbit-mq-ttls))))
      :failure
      status)))

(defn- fail-message-on-reset
  "Mark message as failed due to reset being called on the queue"
  [broker-wrapper msg]
  (update-message-queue-history broker-wrapper :process msg :failure)
  {:status :failure :message "Forced failure on reset"})

(defn- simulate-retry-message
  "Mark message as retrying. If retries are exhausted we mark the message as failed"
  [broker-wrapper msg]
  (let [message-state (queue-response->message-state {:status :retry} msg)]
    (update-message-queue-history broker-wrapper :process msg message-state)
    {:status message-state :message "Simulating retry on message queue"}))

(defn handler-wrapper
  "Wraps handler function to count acks, retries, fails. In addition this wrapper allows for
  a message to be marked as retrying or failed based on the queue configuration for the
  num-retries-atom and the number of times the message has already been retried."
  [broker-wrapper handler]
  (fn [context msg]
    (cond
      ;; Resetting
      @(:resetting?-atom broker-wrapper)
      (fail-message-on-reset broker-wrapper msg)

      ;; Queue set to retry actions N times and this message has not been retried N times
      (< (get msg :retry-count 0) @(:num-retries-atom broker-wrapper))
      (simulate-retry-message broker-wrapper msg)

      :else
      ;; Process the message as normal
      (let [response (handler context msg)
            message-state (queue-response->message-state response msg)]
        (update-message-queue-history broker-wrapper :process msg message-state)
        response))))

(defn create-queue-broker-wrapper
  "Create a BrokerWrapper"
  [broker]
  (->BrokerWrapper broker (atom 0) (atom false) (atom []) (atom 0) (atom false)))
