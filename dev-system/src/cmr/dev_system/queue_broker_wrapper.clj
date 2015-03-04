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

(def message-queue-history
  "A vector of message queue maps. A new message queue map is added every time an action takes
  place on the message queue. A message queue map contains an action and a list of
  messages.  See message-queue-history-example for an example."
  (atom []))

(def valid-action-types
  "A set of the valid action types for a message queue"
  #{:reset
    :enqueue
    :process})

(defn create-message-queue-history-entry
  "Create a message queue history map. Takes an action, the data associated with that action
  and the result of that action."
  [action-type data resulting-state]
  (when-not (valid-action-types action-type)
    (throw (Exception. (str "Unknown action-type: " action-type))))
  (let [data-with-state (when (seq data) (assoc data :state resulting-state))
        messages (case action-type
                   :reset []
                   (:enqueue :process)
                   (when (seq data)
                     (-> (peek @message-queue-history)
                         :messages
                         ;; Replace the given concept ID and revision ID with the updated message
                         (->> (remove #(and (= (:concept-id data) (:concept-id %))
                                            (= (:revision-id data (:revision-id %))))))
                         (conj data-with-state))))
        new-action (util/remove-nil-keys {:action-type action-type
                                          :data data-with-state})]
    {:action new-action
     :messages messages}))


(defn update-message-queue-history
  "TODO"
  [action-type data resulting-state]
  (swap! message-queue-history
         conj
         (create-message-queue-history-entry action-type data resulting-state)))

(comment
  (update-message-queue-history :enqueue
                                {:concept-id "C3-PROV1"
                                 :revision-id 1
                                 :id 1}
                                :initial)

  (update-message-queue-history :reset
                                {}
                                nil)

  (:messages (peek @message-queue-history))
  )

(def message-queue-history-example
  [
   ;; initial message queue state
   {:action {:action-type :reset}
    :messages []}

   ;; message put on the queue
   {:action {:action-type :enqueue
             :data {:concept-id "C1-PROV1"
                    :revision-id 1
                    :id 1
                    :state :initial}}
    :messages [{:concept-id "C1-PROV1"
                :revision-id 1
                :id 1
                :state :initial}]}

   ;; message put on the queue
   {:action {:action-type :enqueue
             :data {:concept-id "C2-PROV1"
                    :revision-id 1
                    :id 2
                    :state :initial}}
    :messages [{:concept-id "C1-PROV1"
                :revision-id 1
                :id 1
                :state :initial}
               {:concept-id "C2-PROV1"
                :revision-id 1
                :id 2
                :state :initial}]}

   ;; Successfully process one
   {:action {:action-type :process
             :data {:concept-id "C1-PROV1"
                    :revision-id 1
                    :id 1
                    :state :initial}}
    :response {:status :ok}
    :messages [{:concept-id "C1-PROV1"
                :revision-id 1
                :id 1
                :state :processed}
               {:concept-id "C2-PROV1"
                :revision-id 1
                :id 2
                :state :initial}]}
   ])

(comment


  (defn current-states
    []
    (map :state (:messages (peek message-queue-history-example))))

  (current-states)

  (defn messages+id->message
    [messages id]
    (first (filter #(= id (:id %)) messages)))

  (defn concept-history
    "Returns a map of concept id revision id tuples to the sequence of states for each one"
    []
    (let [int-states (for [mq message-queue-history-example
                           :when (not= (get-in mq [:action :action-type]) :reset)
                           :let [{{:keys [action-type]
                                   {:keys [concept-id revision-id id]} :data} :action} mq
                                 result-state (:state (messages+id->message (:messages mq) id))]]
                       {[concept-id revision-id] [{:action action-type :result result-state}]})]
      (apply merge-with concat int-states)))

  (concept-history)

  {["C2-PROV1" 1] [{:action :enqueue, :result :initial}],
   ["C1-PROV1" 1] ({:action :enqueue, :result :initial}
                   {:action :process, :result :processed})}
  )



(def message-id-key
  "Key used to track messages within a message map. Each message will have a unique id."
  (keyword (str (ns-name *ns*) "-id")))

;; nil - The inital state of any message.
;; :processed - a message has been successfully processed
;; :failed - a message has failed processing.
(def valid-message-states
  "TODO"
  #{:initial
    :failed
    :processed})

(defn- set-message-state
  "Set the state of a message on the queue"
  [broker-wrapper msg state]
  (when-not (valid-message-states state)
    (throw (Exception. (str "Unknown state: " state))))
  (let [message-state-atom (:message-state-atom broker-wrapper)
        message-id (message-id-key msg)]
    (swap! message-state-atom #(assoc % message-id state))
    state))

(defn handler-wrapper
  "Wraps handler function to count acks, retries, fails"
  [broker-wrapper handler]
  (fn [context msg]
    (if (-> broker-wrapper :resetting?-atom deref)
      (do
        (set-message-state broker-wrapper msg :failed)
        {:status :fail :message "Forced failure on reset"})
      ;; TODO - check whether the queue is in :normal or :failure mode
      ;; if in :normal mode do the same thing as before
      ;; if in :failure mode return
      ;; {:status :retry :message "Queue is in failure mode"}
      (let [resp (handler context msg)
            message-state (case (:status resp)
                            :ok (set-message-state broker-wrapper msg :processed)

                            :retry (when (queue/retry-limit-met? msg (count (iconfig/rabbit-mq-ttls)))
                                     (set-message-state broker-wrapper msg :failed))

                            :fail (set-message-state broker-wrapper msg :failed)

                            (throw (Exception. (str "Invalid response: " (pr-str resp)))))]
        (update-message-queue-history :process msg message-state)
        resp))))

(defn- current-message-states
  "Return the set of all the unique states of the messages currently held by the wrapper."
  [broker-wrapper]
  (let [message-map @(:message-state-atom broker-wrapper)]
    (-> message-map vals set)))

(defn- wait-for-states
  "Wait until the messages that have been enqueued have all reached one of the given
  states.

  TODO document failure states - means immediately fail if you reach any of these states"
  ([broker-wrapper success-states]
   (wait-for-states broker-wrapper success-states
                    (set/difference (disj valid-message-states :initial)
                                    success-states)))
  ([broker-wrapper success-states failure-states]
   (let [succ-states-set (set success-states)]
     (loop [current-states (current-message-states broker-wrapper)]
       (let [non-success-states (set/difference current-states succ-states-set)]
         ;; The current states should only consist of the success states and possibly nil;
         ;; anything else indicates a failure.
         (when (seq non-success-states)

           ;;If we've reached any other state besides a success state
           (when (seq (set/intersection non-success-states failure-states))
             (throw (Exception. (str "Unexpected final message state(s): " non-success-states))))
           (Thread/sleep 100)
           (recur (current-message-states broker-wrapper))))))))

(defrecord BrokerWrapper
  [
   ;; The broker that does the actual work
   queue-broker

   ;; Atom holding the map of message ids to states
   ;; Message states
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

      ;; Set the initial state of the message to :initial
      (set-message-state this tagged-msg :initial)
      (update-message-queue-history :enqueue tagged-msg :initial)

      ;; delegate the request to the wrapped broker
      (queue/publish queue-broker queue-name tagged-msg)))

  (subscribe
    [this queue-name handler params]
    (queue/subscribe queue-broker queue-name handler params))

  (message-count
    [this queue-name]
    (let [qcount (queue/message-count queue-broker queue-name)
          unprocessed-count (count (remove :processed @message-state-atom))]
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
      (reset! message-queue-history [])
      (finally
        (reset! resetting?-atom false)))))

(defn wait-for-indexing
  "Wait for all messages to be marked as processed"
  [broker-wrapper]
  ; (Thread/sleep 10)
  ; nil)
  (wait-for-states broker-wrapper [:processed]))

;; TODO
#_(def valid-message-modes
    "A list of the modes in which the message queue broker can operate.
    :normal - message functions are called and processed as they normally would.
    :retry - all messages return a retry when they are processed."
    #{:normal
      :retry})

;; TODO
#_(defn set-message-mode
    "Used to toggle message queue between normal mode and failure mode. In normal mode messages are
    processed normally. In failure mode all messages return failures. The failure mode is useful for
    automated tests verifying specific behaviors on failure."
    [mode]
    (if (valid-message-modes mode)
      ;; Use an atom to set state?
      "TODO"
      (throw (Exception. (str "Invalid message queue mode: " mode)))))


(defn create-queue-broker-wrapper
  "Create a BrokerWrapper"
  [broker]
  (->BrokerWrapper broker (atom {}) (atom 0) (atom false)))
