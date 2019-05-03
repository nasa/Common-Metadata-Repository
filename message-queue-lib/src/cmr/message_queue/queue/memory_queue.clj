(ns cmr.message-queue.queue.memory-queue
  "Defines an in memory implementation of the Queue protocol. It uses core.async for message passing."
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as a]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as u]
   [cmr.message-queue.config :as config]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]
   [cmr.message-queue.services.queue :as queue]))

(def CHANNEL_BUFFER_SIZE
  "The number of messages that can be placed on a channel before the caller will block."
  100)

(defn- attempt-retry
  "Attempts to retry processing the message unless the retry count has been exceeded."
  [queue-broker queue-name msg resp]
  (let [retry-count (get msg :retry-count 0)]
    (if (queue/retry-limit-met? msg (count (config/time-to-live-s)))
      ;; give up
      (warn "Max retries exceeded for processing message:" (pr-str msg))
      (let [new-retry-count (inc retry-count)
            msg (assoc msg :retry-count new-retry-count)]
        (info "Message" (pr-str msg) "re-queued with response:" (pr-str (:message resp)))
        (info (format "Retrying with retry-count =%d" new-retry-count))
        (queue-protocol/publish-to-queue queue-broker queue-name msg)))))

(defn- create-async-handler
  "Creates a go block that will asynchronously pull messages off the queue, pass them to the handler,
  and process the response."
  [queue-broker queue-name handler]
  (let [queue-ch (get-in queue-broker [:queues-to-channels queue-name])]
    (a/go
      (try
        (u/while-let
          [msg (a/<! queue-ch)]
          (let [msg (json/decode msg true)]
            (try
              (handler msg)
              (catch Throwable e
                (error e "Message processing failed for message" (pr-str msg))
                ;; Retry by requeueing the message
                (attempt-retry queue-broker queue-name msg {:message (.getMessage e)})))))
        (finally
          (info "Async go handler for queue" queue-name "completing."))))))

(defn drain-channels
  "Removes all messages from the given channels. Will not block"
  [channels]
  (when channels
    (loop []
      (when-not (= :done (first (a/alts!! (vec channels) :default :done)))
        (recur)))))

(defrecord MemoryQueueBroker
  [
   ;; A list of queue names
   queues

   ;; A map of exchange names to sets of queue names to which the exchange will broadcast.
   exchanges-to-queue-sets

   ;; A map of queue names to core async channels containing messages to deliver
   queues-to-channels

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Running State

   ;; An atom containing a sequence of channels returned by the go block processors for each handler.
   handler-channels-atom]


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    this)

  (stop
    [this system]
    (drain-channels (vals queues-to-channels))

    ;; Wait for go blocks to finish
    (doseq [handler-ch @handler-channels-atom]
      (a/close! handler-ch)
      (let [[_ ch] (a/alts!! [(a/timeout 2000)
                              handler-ch])]
        (when-not (= ch handler-ch)
          (warn "Timed out waiting for go block to complete"))))
    (reset! handler-channels-atom nil)

    this)
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue-protocol/Queue

  (publish-to-queue
    [this queue-name msg]
    ;; Puts the message on the channel. It is encoded as json to simulate the Rabbit MQ behavior
    (if-let [chan (queues-to-channels queue-name)]
      (a/>!! chan (json/generate-string msg))
      (throw (IllegalArgumentException. (str "Could not find channel bound to queue " queue-name)))))

  (get-queues-bound-to-exchange
    [this exchange-name]
    (seq (exchanges-to-queue-sets exchange-name)))

  (publish-to-exchange
    [this exchange-name msg]
    (every? #(queue-protocol/publish-to-queue this % msg)
            (exchanges-to-queue-sets exchange-name)))

  (subscribe
    [this queue-name handler]
    (swap! handler-channels-atom conj (create-async-handler this queue-name handler))
    nil)

  (reset
    [this]
    ;; clear all channels
    (drain-channels (vals queues-to-channels)))

  (reconnect
    [this]
    (try
      (lifecycle/stop this nil)
      (catch Exception e
        (warn e "Failed to properly stop in call to reconnect.")))
    (lifecycle/start this nil)
    this)

  (health
    [this]
    {:ok? true}))
(record-pretty-printer/enable-record-pretty-printing MemoryQueueBroker)

(defn create-memory-queue-broker
  "Creates a memory queue with the given parameters. This should match the same parameters of the
  RabbitMQBroker"
  [{:keys [queues exchanges queues-to-exchanges]}]
  (let [exchanges-to-empty-sets (into {} (for [e exchanges] [e #{}]))

        ;; Create a sequence of all the exchange queue combinations
        exchange-queue-tuples (for [[queue exchanges] queues-to-exchanges
                                    exchange exchanges]
                                [exchange queue])

        ;; Create a map of exchange names to sets of queues
        exchanges-to-queue-sets (reduce (fn [e-to-q [exchange queue]]
                                          (update-in e-to-q [exchange] conj queue))
                                        exchanges-to-empty-sets
                                        exchange-queue-tuples)
        q-to-chans (into {} (for [q queues] [q (a/chan CHANNEL_BUFFER_SIZE)]))]
    (->MemoryQueueBroker queues exchanges-to-queue-sets
                         q-to-chans
                         (atom nil))))

(comment

  (def qb (create-memory-queue-broker {:queues ["a" "b" "c"]
                                       :exchanges ["e1" "e2"]
                                       :queues-to-exchanges {"a" ["e1"], "b" ["e1"], "c" ["e2"]}}))

  (def running-qb (lifecycle/start qb nil))

  (def stopped-qb (lifecycle/stop running-qb nil))

  (do
    (defn message-handler
      [queue-name msg]
      (println "Handling" (pr-str msg) "from queue" queue-name)
      {:status :success})

    (queue/subscribe running-qb "a" (partial message-handler "a"))
    (queue/subscribe running-qb "b" (partial message-handler "b"))
    (queue/subscribe running-qb "c" (partial message-handler "c")))

  (queue/publish-to-queue running-qb "a" {:id 1})
  (queue/publish-to-queue running-qb "b" {:id 2})
  (queue/publish-to-queue running-qb "c" {:id 3})

  (queue/publish-to-exchange running-qb "e1" {:id 4})
  (queue/publish-to-exchange running-qb "e2" {:id 5}))
