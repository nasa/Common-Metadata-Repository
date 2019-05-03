(ns cmr.message-queue.test.queue-broker-wrapper
  "Functions to wrap the message queue while testing. The wrapper is necessary because messages
  are processed asynchronously, but for our tests we will often want to wait until messages are
  processed before performing other steps or confirming results. It keeps track, in memory, of
  every message sent to the message queue. It has the ability to wait until each one of these
  messages has been processed. For this to work we have to use the same queue broker wrapper
  instance on the sender and receiver. This means they both need to be in the same JVM instance."
  (:require
   [clojure.set :as set]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug trace info warn error)]
   [cmr.common.util :as util]
   [cmr.message-queue.config :as iconfig]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]
   [cmr.message-queue.services.queue :as queue]))

(def ^:const ^:private valid-action-types
  "A set of the valid action types for a message queue:
  :enqueue - Message has been added to the queue.
  :process - Message has been processed."
  #{:enqueue
    :process})

(defn- append-to-message-queue-history
  "Create a message queue history map and append it to the queue history given.

  Parameters:
  queue-history - history of the queue
  action-type - A keyword representing the action taken on the message queue.
  message - Map with :id, :action (either \"index-concept\" or \"delete-concept\"), :concept-id, and
  :revision-id. message can be nil for messages unrelated to a concept.
  resulting-state - One of the valid message states

  Example message-queue-history-map:
  {:action {:action-type :enqueue
            :message {:id 1 :concept-id \"C1-PROV1\" :revision-id 1 :state :initial}}
   :messages [{:id 1 :concept-id \"C1-PROV1\" :revision-id 1 :state :initial}]}"
  [queue-history action-type message resulting-state]
  {:pre [(valid-action-types action-type) message]}
  (let [message-with-state (assoc message :state resulting-state)
        messages (conj
                   ;; Messages are unique based on id - if the action is to change the state
                   ;; of a message we already know about, we replace the original message
                   ;; with the new one in our list of messages.
                   (remove #(= (:id message) (:id %))
                           (-> queue-history last :messages))
                   message-with-state)
        new-action (util/remove-nil-keys {:action-type action-type
                                          :message message-with-state})]
    (conj queue-history {:action new-action :messages messages})))

(defn- update-message-queue-history
  "Called when an event occurs on the message queue in order to add a new entry to the message
  queue history. See append-to-message-queue-history for a description of the parameters."
  [broker-wrapper queue-name action-type data resulting-state]
  (swap! (:message-queue-history-atom broker-wrapper)
         (fn [message-queue-history-value]
           (let [queue-history (get message-queue-history-value queue-name [])
                 new-history (append-to-message-queue-history
                               queue-history action-type data resulting-state)]
             (assoc message-queue-history-value queue-name new-history)))))

(def ^:const ^:private valid-message-states
  "Set of valid message states:
  :initial - message first created
  :retry - the message failed processing and is currently retrying
  :failure - the message failed processing and all retries have been exhausted
  :success - the message has completed successfully"
  #{:initial
    :retry
    :failure
    :success})

(def ^:const ^:private terminal-states
  "Subset of valid message states which are considered final. Used to determine when a message will
  no longer be processed."
  #{:failure
    :success})

(defn- current-messages
  "Return a list of the current messages."
  [broker-wrapper]
  (for [[_ queue-history] (when (:message-queue-history-atom broker-wrapper)
                            (deref (:message-queue-history-atom broker-wrapper)))
        msg (:messages (last queue-history))]
    msg))

(defn- current-message-states
  "Return a sequence of message states for all messages currently held by the wrapper."
  [broker-wrapper]
  (map :state (current-messages broker-wrapper)))

(defn wait-for-terminal-states
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
           (warn (format "Waited %d ms for messages to complete, but they did not complete. In progress: %s"
                         ms-to-wait
                         (pr-str (remove #(contains? terminal-states (:state %))
                                         (current-messages broker-wrapper)))))))))))

(defn- publish-to-queue
  "Publishes a message to the queue and captures the actions with the queue history."
  [broker queue-name msg]
  ;; record the message
  (let [{:keys [id-sequence-atom timeout?-atom queue-broker]} broker
        msg-id (swap! id-sequence-atom inc)
        tagged-msg (assoc msg :id msg-id)]
    ;; Set the initial state of the message to :initial
    (update-message-queue-history broker queue-name :enqueue tagged-msg :initial)
    (trace "Updated message queue history")
    ;; Mark the enqueue as failed if we are timing things out or it fails
    (if (or @timeout?-atom (not (queue-protocol/publish-to-queue queue-broker queue-name tagged-msg)))
      (do
        (trace "Published; preparing to update history ...")
        (update-message-queue-history broker queue-name :enqueue tagged-msg :failure)
        false)
      true)))

(defn- publish-to-exchange
  "Publishes a message to the exchange and captures the actions with the queue history of the
  exchanges associated queues."
  [broker exchange-name msg]
  (let [queues (queue-protocol/get-queues-bound-to-exchange
                (:queue-broker broker) exchange-name)
        results (for [queue-name queues]
                  (publish-to-queue broker queue-name msg))]
    (every? identity results)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler wrapper functions

(defn- queue-response->message-state
  "Converts the response of a message queue action to the appropriate message state. If a message
  has a retry status and has exceeded the max retries then return failure."
  [status msg]
  {:pre [(valid-message-states status)]}
  (if (and (= :retry status)
           (queue/retry-limit-met? msg (count (iconfig/time-to-live-s))))
    :failure
    status))

(defn- fail-message-on-reset
  "Mark message as failed due to reset being called on the queue"
  [broker-wrapper queue-name msg]
  (update-message-queue-history broker-wrapper queue-name :process msg :failure)
  (throw (Exception. "Forced failure on reset")))

(defn- simulate-retry-message
  "Mark message as retrying. If retries are exhausted we mark the message as failed"
  [broker-wrapper queue-name msg]
  (let [message-state (queue-response->message-state :retry msg)]
    (update-message-queue-history broker-wrapper queue-name :process msg message-state)
    (throw (Exception. "Simulating retry on message queue"))))

(defn- handler-wrapper
  "Wraps handler function to count acks, retries, fails. In addition this wrapper allows for
  a message to be marked as retrying or failed based on the queue configuration for the
  num-retries-atom and the number of times the message has already been retried."
  [broker-wrapper queue-name handler]

  (fn [msg]
    (cond
      ;; Resetting
      @(:resetting?-atom broker-wrapper)
      (fail-message-on-reset broker-wrapper queue-name msg)

      ;; Queue set to retry actions N times and this message has not been retried N times
      (< (get msg :retry-count 0) @(:num-retries-atom broker-wrapper))
      (simulate-retry-message broker-wrapper queue-name msg)

      :else
      ;; Process the message as normal
      (try
        (handler msg)
        (update-message-queue-history broker-wrapper queue-name :process msg :success)
        (catch Exception e
          ;; Will return a retry up to the retry count and then a failure afterwards
          (let [message-state (queue-response->message-state :retry msg)]
            (update-message-queue-history broker-wrapper queue-name :process msg message-state))
          (throw e))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Record Definition

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

   ;; Contains a map of each queue name to a history of the queue represented as a vector of message
   ;; queue state maps. Each message queue state map contains an :action map indicating the action
   ;; that caused the message queue to change state and a :messages sequence of the messages in the
   ;; queue.
   message-queue-history-atom


   ;; Number of times every request should return an error response indicating retry prior to being
   ;; processed normally. Useful for automated tests verifying specific behaviors on retry and
   ;; failure.
   num-retries-atom

   ;; Tracks whether enqueuing messages will fail with a timeout error.
   timeout?-atom]


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
  queue-protocol/Queue

  (publish-to-queue
    [this queue-name msg]
    (publish-to-queue this queue-name msg))

  (publish-to-exchange
    [this exchange-name msg]
    (publish-to-exchange this exchange-name msg))

  (subscribe
    [this queue-name handler]
    ;; Wrap the handler with another function to allow counting retries etc.
    (queue-protocol/subscribe queue-broker queue-name (handler-wrapper this queue-name handler)))

  (reset
    [this]
    (reset! resetting?-atom true)
    (try
      (wait-for-terminal-states this)
      (queue-protocol/reset queue-broker)
      (reset! id-sequence-atom 0)
      (reset! message-queue-history-atom {})
      (finally
        (reset! resetting?-atom false))))

  (health
    [this]
    (queue-protocol/health queue-broker)))

(record-pretty-printer/enable-record-pretty-printing BrokerWrapper)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-queue-broker-wrapper
  "Create a BrokerWrapper"
  [broker]
  (->BrokerWrapper broker (atom 0) (atom false) (atom {}) (atom 0) (atom false)))

(defn get-message-queue-history
  "Returns the message-queue-history."
  [broker-wrapper queue-name]
  (get @(:message-queue-history-atom broker-wrapper) queue-name))

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
  (reset! (:timeout?-atom broker-wrapper) timeout?))
