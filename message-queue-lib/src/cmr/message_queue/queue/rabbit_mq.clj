(ns cmr.message-queue.queue.rabbit-mq
  "Implements index-queue functionality using rabbit mq"
  (:gen-class)
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.message-queue.config :as config]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.queue :as queue]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lc]
            [langohr.confirm :as lcf]
            [langohr.basic :as lb]
            [langohr.exchange  :as le]
            [langohr.http :as lhttp]
            [cheshire.core :as json]
            [clojure.math.numeric-tower :as math])
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

(def ^{:const true}
  default-exchange-name "")

(defn- attempt-retry
  "Retry a message if it has not already exceeded the allowable retries"
  [queue-broker ch queue-name routing-key msg delivery-tag resp]
  (let [retry-count (get msg :retry-count 0)]
    (if (queue/retry-limit-met? msg (count (config/rabbit-mq-ttls)))
      (do
        ;; give up
        (warn "Max retries exceeded for processing message:" (pr-str msg))
        (lb/nack ch delivery-tag false false))
      (let [msg (assoc msg :retry-count (inc retry-count))
            wait-q (wait-queue-name routing-key (inc retry-count))
            ttl (wait-queue-ttl (inc retry-count))]
        (debug "Message" (pr-str msg) "re-queued with response:" (pr-str (:message resp)))
        (debug "Retrying with retry-count ="
               (inc retry-count)
               " on queue"
               wait-q
               "with ttl = "
               ttl)
        (queue/publish queue-broker wait-q msg)
        (lb/ack ch delivery-tag)))))

(defn- start-consumer
  "Starts a message consumer in a separate thread.

  'queue-broker' is a record that implements
  both the Queue and Lifecycle protocols for interacting with a message queue.

  'queu-name' is the identifier of the queue to use to receive messages and should correpsond to the
  identifier used to create the queue with the create-queue function.

  'client-handler' is a function that takes
  a single parameter (the message) and attempts to process it. This function should respond with
  a map of the of the follwing form:
  {:status status :message message}
  where status is one of (:ok, :retry, :fail) and message is optional.

  'params' is a map containing queue implementation specific settings, if necessary. Currently the
  only supported setting for RabbitMQ is :prefetch, which corresponds to the number of messages
  a consumer should pull off the queue at one time."
  [queue-broker queue-name client-handler params]
  (let [{:keys [prefetch]} params
        conn (:conn queue-broker)
        sub-ch (lch/open conn)
        handler (fn [ch {:keys [delivery-tag type routing-key] :as meta} ^bytes payload]
                  (let [msg (json/parse-string (String. payload) true)]
                    (debug "Received message:" (pr-str msg))
                    (try
                      (let [resp (client-handler msg)]
                        (case (:status resp)
                          :ok (do
                                ;; processed successfully
                                (debug "Message" (pr-str msg) "processed successfully")
                                (lb/ack ch delivery-tag))
                          :retry (attempt-retry queue-broker ch queue-name
                                                routing-key msg delivery-tag resp)
                          :fail (do
                                  ;; bad data - nack it
                                  (warn "Rejecting bad data:" (pr-str (:message resp)))
                                  (lb/nack ch delivery-tag false false))))
                      (catch Throwable e
                        (error "Message processing failed for message" (pr-str msg) "with error:"
                               (.getMessage e))
                        (attempt-retry queue-broker ch queue-name routing-key msg delivery-tag
                                       {:message (.getMessage e)})))))]
    ;; By default, RabbitMQ uses a round-robin approach to send messages to listeners. This can
    ;; lead to stalls as workers wait for other workers to complete. Setting QoS (prefetch) to 1
    ;; prevents this.
    (lb/qos sub-ch (or prefetch 1))
    (lc/subscribe sub-ch queue-name handler {:auto-ack false})))

(defn- safe-create-queue
  "Creates a RabbitMQ queue if it does not already exist

  'ch' - The RabbitMQ/Langohr channel used for making the request
  'queue-name' - The desired identifier for the queue
  'opts' - Langhor configuration parameters for the queue
  (see http://reference.clojurerabbitmq.info/langohr.queue.html)"
  [ch queue-name opts]
  (lq/declare ch queue-name opts))

(defrecord RabbitMQBroker
  [
   ;; RabbitMQ server host
   host

   ;; RabbitMQ server port
   port

   ;; RabbitMQ user name
   username

   ;; RabbitMQ password
   password

   ;; Connection to the message queue
   conn

   ;; Long-lived channel for publications
   pub-ch

   ;; Queues that should be created on startup
   persistent-queues

   ;; true or false to indicate it's running
   running?
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting RabbitMQ connection")
    (when (:running? this)
      (errors/internal-error! "Already connected"))
    (let [{:keys [host port username password]} this
          conn (rmq/connect {:host host :port port :username username :password password})
          pub-ch (lch/open conn)
          ;; put channel into confirmation mode
          _ (lcf/select pub-ch)
          this (assoc this :conn conn :pub-ch pub-ch :running? true)]
      (info "RabbitMQ connection opened")
      (doseq [queue-name (:persistent-queues this)]
        (queue/create-queue this queue-name))
      this))

  (stop
    [this system]
    (when (:running? this)
      ;; Close the connection. This will close all channels as well.
      (rmq/close (:conn this))
      (assoc this :running? false)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue/Queue

  (create-queue
    [this queue-name]
    ;; create the queue
    (safe-create-queue pub-ch queue-name {:exclusive false :auto-delete false :durable true})
    ;; create wait queues to use with the primary queue
    (info "Creating wait queues")
    (doseq [wait-queue-num (range 1 (inc (count (config/rabbit-mq-ttls))))
            :let [ttl (wait-queue-ttl wait-queue-num)
                  wq (wait-queue-name queue-name wait-queue-num)]]
      (safe-create-queue pub-ch
                         wq
                         {:exclusive false
                          :auto-delete false
                          :durable true
                          :arguments {"x-dead-letter-exchange" default-exchange-name
                                      "x-dead-letter-routing-key" queue-name
                                      "x-message-ttl" ttl}})))

  (publish
    [this queue-name msg]
    (debug "publishing msg:" (pr-str msg) " to queue:" queue-name)
    (let [payload (json/generate-string msg)
          metadata {:content-type "application/json" :persistent true}]
      ;; publish the message
      (lb/publish pub-ch default-exchange-name queue-name payload metadata)
      ;; block until the confirm arrives or return false if queue nacks the message
      (lcf/wait-for-confirms pub-ch)))

  (subscribe
    [this queue-name handler params]
    (start-consumer this queue-name handler params))

  (message-count
    [this queue-name]
    (debug "Getting message count for queue" queue-name " on channel" pub-ch)
    (lq/message-count pub-ch queue-name))

  (reset
    [this]
    (debug "Resetting RabbitMQ")
    ;; TODO reset RabbitMQ
    )
)
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (defn purge-queue
    "Remove all messages from a queue and the associated wait queues"
    [broker queue-name]
    (info "Purging all messages from queue" queue-name)
    (lq/purge (:pub-ch broker) queue-name)
    (doseq [wait-queue-num (range 1 (inc (count (config/rabbit-mq-ttls))))
            :let [ttl (wait-queue-ttl wait-queue-num)
                  wq (wait-queue-name queue-name wait-queue-num)]]
      (debug "Purging messages from wait queue" wq)
      (lq/purge (:pub-ch broker) wq)))

  (defn delete-queue
    [broker queue-name]
    (lq/delete (:pub-ch broker) queue-name)
    (doseq [wait-queue-num (range 1 (inc (count (config/rabbit-mq-ttls))))
            :let [ttl (wait-queue-ttl wait-queue-num)
                  wq (wait-queue-name queue-name wait-queue-num)]]
      (lq/delete (:pub-ch broker) wq)))

  (defn create-queue-broker
    "Create a RabbitMQBroker"
    [params]
    (let [{:keys [host port username password queues]} params]
      (->RabbitMQBroker host port username password nil nil queues false)))


  (comment

    (do
      (def q (let [q (create-queue-broker {:host (config/rabbit-mq-host)
                                           :port (config/rabbit-mq-port)
                                           :username (config/rabbit-mq-username)
                                           :password (config/rabbit-mq-password)
                                           ;:queues ["test.simple"]})]
                                           })]
               (lifecycle/start q {})))

      (defn test-message-handler
        [msg]
        (info "Test handler")
        (let [val (rand)
              rval (cond
                     (> 0.5 val) {:status :ok}
                     (> 0.97 val) {:status :retry :message "service down"}
                     :else {:status :fail :message "bad data"})]

          rval))

      (queue/subscribe q "test.simple" test-message-handler {})

      (doseq [n (range 0 1000)
              :let [concept-id (str "C" n "-PROV1")
                    msg {:action :index-concept
                         :concept-id concept-id
                         :revision-id 1}]]
        (queue/publish q "test.simple" msg))

      )

    (queue/create-queue q "cmr_index.queue")

    (queue/purge-queue q "test.simple")

    (queue/message-count q "test.simple")

    (queue/delete-queue q "cmr_index.queue")

    (queue/purge-queue q "cmr_index.queue")

    (lifecycle/stop q {})

    )


