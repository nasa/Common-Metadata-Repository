(ns cmr.message-queue.queue.rabbit-mq
  "Implements index-queue functionality using rabbit mq"
  (:gen-class)
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.mime-types :as mt]
            [cmr.message-queue.config :as config]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.queue :as queue]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lc]
            [langohr.confirm :as lcf]
            [langohr.basic :as lb]
            [langohr.exchange :as le]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.common.services.health-helper :as hh]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer])
  (:import java.io.IOException))

(defmacro with-channel
  "Opens and binds a channel to the given name from a connection, executes the body and then closes the channel.
  Example:

  (with-channel
  [chan conn]
  (lq/status chan \"my-queue\"))"
  [bindings & body]
  (when (not= 2 (count bindings))
    (throw (Exception. "Expected a single pair of bindings of a channel symbol and a connection")))
  (let [[channel-name connection-name] bindings]
    `(let [~channel-name (lch/open ~connection-name)]
       (try
         ~@body
         (finally
           (lch/close ~channel-name))))))

(defn wait-queue-name
  "Returns the name of the wait queue associated with the given queue name and retry-count"
  [queue-name retry-count]
  (str queue-name "_wait_" retry-count))

(defn- wait-queue-ttl
  "Returns the Time-To-Live (TTL) in milliseconds for the nth (1 based) wait queue"
  [n]
  (* 1000 (nth (config/rabbit-mq-ttls) (dec n))))

(def ^:const default-exchange-name "")

(defn- attempt-retry
  "Retry a message if it has not already exceeded the allowable retries"
  [queue-broker ch queue-name msg delivery-tag resp]
  (let [retry-count (get msg :retry-count 0)]
    (if (queue/retry-limit-met? msg (count (config/rabbit-mq-ttls)))
      (do
        ;; give up
        ;; Splunk alert "Indexing from message queue failed and all retries exhausted" dependent on this log message
        (warn "Max retries exceeded for processing message:" (pr-str msg) "on queue:" queue-name)
        (lb/nack ch delivery-tag false false))
      (let [msg (assoc msg :retry-count (inc retry-count))
            wait-q (wait-queue-name queue-name (inc retry-count))
            ttl (wait-queue-ttl (inc retry-count))]
        (info "Message" (pr-str msg) "re-queued with response:" (pr-str (:message resp)))
        (info (format "Retrying with retry-count =%d on queue %s with ttl = %d"
                      (inc retry-count)
                      wait-q
                      ttl))
        (queue/publish-to-queue queue-broker wait-q msg)
        (lb/ack ch delivery-tag)))))

(defn- message-handler
  "Processes a given message and determines what to do based on the response code returned. Send an
  ack if the message completed successfully. Put the message on a wait queue if the response
  indicates it needs to be retried. Nack the message if the response is marked as failed."
  [queue-broker queue-name client-handler ch metadata ^bytes payload]
  (let [{:keys [delivery-tag]} metadata
        msg (json/parse-string (String. payload) true)]
    (try
      (client-handler msg)
      (lb/ack ch delivery-tag)
      (catch Throwable e
        (error e "Message processing failed for message" (pr-str msg))
        (attempt-retry queue-broker ch queue-name msg delivery-tag
                       {:message (.getMessage e)})))))

(defn- start-consumer
  "Starts a message consumer in a separate thread.

  'queue-broker' is a record that implements
  both the Queue and Lifecycle protocols for interacting with a message queue.

  'queue-name' is the identifier of the queue to use to receive messages and should correpsond to
  the identifier used to create the queue with the create-queue function.

  'client-handler' is a function that takes a single parameter (the message) and attempts to
  process it. If a function throws an exception it will be retried."
  [queue-broker queue-name client-handler]
  (let [conn (:conn queue-broker)
        ;; don't want this channel to close automatically, so no with-channel here
        sub-ch (lch/open conn)
        handler (partial message-handler queue-broker queue-name client-handler)]
    ;; By default, RabbitMQ uses a round-robin approach to send messages to listeners. This can
    ;; lead to stalls as workers wait for other workers to complete. Setting QoS (prefetch) to 1
    ;; prevents this.
    (lb/qos sub-ch 1)
    (lc/subscribe sub-ch queue-name handler {:auto-ack false})))

(defn purge-queue
  "Remove all messages from a queue and the associated wait queues"
  [broker queue-name]
  (let [conn (:conn broker)]
    (with-channel [ch conn]
                  (info "Purging all messages from queue" queue-name)
                  (lq/purge ch queue-name)
                  (doseq [wait-queue-num (range 1 (inc (count (config/rabbit-mq-ttls))))
                          :let [ttl (wait-queue-ttl wait-queue-num)
                                wq (wait-queue-name queue-name wait-queue-num)]]
                    (debug "Purging messages from wait queue" wq)
                    (lq/purge ch wq)))))

(defn delete-queue
  "Remove a queue from the RabbitMQ server/cluster"
  [broker queue-name]
  (let [conn (:conn broker)]
    (with-channel [ch conn]
                  (lq/delete ch queue-name)
                  (doseq [wait-queue-num (range 1 (inc (count (config/rabbit-mq-ttls))))
                          :let [ttl (wait-queue-ttl wait-queue-num)
                                wq (wait-queue-name queue-name wait-queue-num)]]
                    (lq/delete ch wq)))))

(defn health-fn
  "Check that the queue is up and responding"
  [queue-broker]
  (try
    (let [{:keys [host admin-port username password]} queue-broker
          {:keys [status body]} (client/request
                                  {:url (format "http://%s:%s/api/aliveness-test/%s"
                                                host admin-port
                                                "%2f")
                                   :method :get
                                   :throw-exceptions false
                                   :basic-auth [username password]})]
      (if (= status 200)
        (let [rmq-status (-> body (json/decode true) :status)]
          (if (= rmq-status "ok")
            {:ok? true}
            {:ok? false :problem rmq-status}))
        {:ok? false :problem body}))
    (catch Exception e
      {:ok? false :problem (.getMessage e)})))

(defn- publish
  "Publishes a message to the given exchange/queue combination. When publishing to a queue set the
  exchange name to an empty string and the routing-key to the queue name."
  [queue-broker exchange-name routing-key msg]
  (let [payload (json/generate-string msg)
        metadata {:content-type mt/json :persistent true}]
    (with-channel
      [pub-ch (:conn queue-broker)]
      ;; put channel into confirmation mode
      (lcf/select pub-ch)

      ;; publish the message
      (lb/publish pub-ch exchange-name routing-key payload metadata)
      ;; block until the confirm arrives or return false if queue nacks the message
      (lcf/wait-for-confirms pub-ch))))

(defn- create-queue
  [broker queue-name]
  (let [{:keys [conn]} broker]
    (with-channel
      [ch conn]
      ;; create the queue
      ;; see http://reference.clojurerabbitmq.info/langohr.queue.html
      (info "Creating queue" queue-name)
      (lq/declare ch queue-name {:exclusive false :auto-delete false :durable true})
      ;; create wait queues to use with the primary queue
      (info "Creating wait queues")
      (doseq [wait-queue-num (range 1 (inc (count (config/rabbit-mq-ttls))))
              :let [ttl (wait-queue-ttl wait-queue-num)
                    wq (wait-queue-name queue-name wait-queue-num)]]
        (lq/declare ch
                    wq
                    {:exclusive false
                     :auto-delete false
                     :durable true
                     :arguments {"x-dead-letter-exchange" default-exchange-name
                                 "x-dead-letter-routing-key" queue-name
                                 "x-message-ttl" ttl}})))))
(defn- create-exchange
  [broker exchange-name]
  (let [{:keys [conn]} broker]
    (with-channel
      [ch conn]
      (info "Creating exchange" exchange-name)
      (le/declare ch exchange-name "fanout" {:durable true}))))

(defn- bind-queue-to-exchange
  [broker queue-name exchange-name]
  (let [{:keys [conn]} broker]
    (with-channel
      [ch conn]
      (info "Binding queue" queue-name "to exchange" exchange-name)
      (lq/bind ch queue-name exchange-name))))

(defrecord RabbitMQBroker
  [
   ;; RabbitMQ server host
   host

   ;; RabbitMQ server port for queueing requests
   port

   ;; RabbitMQ server port for admin requests (http)
   admin-port

   ;; RabbitMQ user name
   username

   ;; RabbitMQ password
   password

   ;; Connection to the message queue
   conn

   ;; Queues that should be created on startup
   persistent-queues

   ;; Exchanges that should be created on startup
   persistent-exchanges

   ;; A map of queue name to a sequence of exchange names that the queue should be bound to.
   bindings]


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting RabbitMQ connection")
    (let [conn (rmq/connect {:host host :port port :username username :password password})
          this (assoc this :conn conn)]
      (info "RabbitMQ connection opened")
      (doseq [queue-name persistent-queues]
        (create-queue this queue-name))

      (doseq [exchange-name persistent-exchanges]
        (create-exchange this exchange-name))

      (doseq [[queue-name exchange-names] bindings
              exchange-name exchange-names]
        (bind-queue-to-exchange this queue-name exchange-name))
      this))

  (stop
    [this system]
    ;; Close the connection. This will close all channels as well.
    (when (:conn this) (rmq/close (:conn this)))
    (assoc this :conn nil))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue/Queue

  (publish-to-queue
    [this queue-name msg]
    (publish this default-exchange-name queue-name msg))

  (get-queues-bound-to-exchange
    [this exchange-name]
    (for [[q es] bindings
          e es
          :when (= e exchange-name)]
      q))

  (publish-to-exchange
    [this exchange-name msg]
    (publish this exchange-name "" msg))

  (subscribe
    [this queue-name handler]
    (start-consumer this queue-name handler))

  (reset
    [this]
    (info "Resetting RabbitMQ")
    (doseq [queue-name persistent-queues]
      (purge-queue this queue-name)))

  (health
    [this]
    (let [timeout-ms (* 1000 (+ 2 (hh/health-check-timeout-seconds)))]
      (hh/get-health #(health-fn this) timeout-ms))))

(record-pretty-printer/enable-record-pretty-printing RabbitMQBroker)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-queue-broker
  "Create a RabbitMQBroker with the given parameters. See RabbitMQBroker comments for information"
  [{:keys [host port admin-port username password
           queues exchanges queues-to-exchanges]}]
  (->RabbitMQBroker host port admin-port username password nil
                    queues exchanges queues-to-exchanges))
