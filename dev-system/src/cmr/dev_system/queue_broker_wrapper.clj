(ns cmr.dev-system.queue-broker-wrapper
  "Functions to wrap the message queue while testing"
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.message-queue.services.queue :as queue]
            [cmr.message-queue.config :as iconfig]
            [cmr.common.log :as log :refer (debug info warn error)]))

(defn- set-message-state
  "Set the state of a message on the queue"
  [broker-wrapper msg state]
  (let [bare-msg (-> (dissoc msg :retry-count) (update-in [:action] keyword))
        messages-atom (:messages-atom broker-wrapper)
        messages @messages-atom
        updated-msg (assoc bare-msg :state state)]
    (swap! messages-atom #(replace {bare-msg updated-msg} %))))

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
                   (debug "Setting wrapper state to :failed for message" msg)
                   (set-message-state broker-wrapper msg :failed))

          ;; treat nacks as acks for counting purposes
          :fail (set-message-state broker-wrapper msg :failed)

          (throw (Exception. (str "Invalid response: " (pr-str resp)))))
        resp))))

(defn- wait-for-states
  "Wait until the messages that have been enqueued have all reached one of the given
  states."
  [broker-wrapper success-states]
  (let [succ-states-set (set success-states)]
    (loop [messages @(:messages-atom broker-wrapper)]
      (when (some (fn [msg]
                    (when-let [state (:state msg)]
                      (not (contains? succ-states-set state))))
                  messages)
        (throw (Exception. (str "Unexpected final message state"))))
      (when (some #(nil? (:state %)) messages)
        (debug "SLEEPING.....")
        (Thread/sleep 100)
        (debug (pr-str messages))
        (recur @(:messages-atom broker-wrapper))))))

(defrecord BrokerWrapper
  [
   ;; the broker that does the actual work
  	queue-broker

   ;; atom holding the list of messages
   messages-atom

   ;; atom holding the resetting flag
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
    (swap! messages-atom #(conj % msg))
    (queue/publish queue-broker queue-name msg))

  (subscribe
    [this queue-name handler params]
    (queue/subscribe queue-broker queue-name handler params))

  (message-count
    [this queue-name]
    (let [qcount (queue/message-count queue-broker queue-name)
          unprocessed-count (count (filter (complement :processed) @messages-atom))]
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
      (reset! (:messages-atom this) [])
      (finally
        (reset! (:resetting?-atom this) false)))))

(defn internal-message-counts
  "Get the counts of messages that have been published"
  [wrapper]
  (let [messages-atom (:messages-atom wrapper)
        processed-count (count (filter #(= :processed (:state %)) @messages-atom))
        unprocessed-count (- (count @messages-atom) processed-count)]
    {:processed processed-count :unprocessed unprocessed-count}))


(defn wait-for-indexing
  "Wait for all messages to be marked as processed"
  [broker-wrapper]
  (debug "Waiting for all indexing messages to be processed")
  (wait-for-states broker-wrapper [:processed])
  (debug "All messages processed"))

(defn create-queue-broker-wrapper
  "Create a BrokerWrapper"
  [broker]
  (->BrokerWrapper broker (atom []) (atom false)))
