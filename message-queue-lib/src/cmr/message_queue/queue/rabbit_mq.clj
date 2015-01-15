(ns cmr.message-queue.queue.rabbit-mq
  "Implements index-queue functionality using rabbit mq"
  (:gen-class)
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.message_queue.config :as config]
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
            [clojure.math.numeric-tower :as math]))

(defn wait-queue-name
  "Returns the name of the wait queue associated with the given queue name and repeat-count"
  [queue-name repeat-count]
  (str queue-name "_wait_" repeat-count))

(def ^{:const true}
  default-exchange-name "")

(defn message-metadata
  "Creates a map with the appropriate metadata for our messages"
  [queue-name message-type]
  ;; TODO - Should the :content-type be application/json? Does this matter?
  {:routing-key queue-name :content-type "text/plain" :type message-type :persistent true})

(defn- start-consumer
  "Starts a message consumer in a separate thread"
  [conn queue-name handler params]
  (let [{:keys [prefetch]} params
        ch (lch/open conn)]
    ;; By default, RabbitMQ uses a round-robin approach to send messages to listeners. This can
    ;; lead to stalls as workers wait for other workers to complete. Setting QoS (prefetch) to 1
    ;; prevents this.
    (debug "Setting channel prefetch")
    (lb/qos ch (or prefetch 1))
    (lc/subscribe ch queue-name handler {:auto-ack false})))

(defn- safe-create-queue
  "Creates a RabbitMQ queue if it does not already exist"
  [ch queue-name opts]
  ;; use status to determine if queue already exists
  ;(try
  ;  (lq/status ch queue-name)
  ;  (catch IOException e
      (lq/declare ch queue-name opts))
;))

(defrecord RabbitMQConnection
  [
   ;; RabbitMQ server host
   host

   ;; RabbitMQ server port
   port

   ;; Username
   username

   ;; Password
   password

   ;; Connection to the message queue
   conn

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
    (let [{:keys [host port user password]} this
          conn (rmq/connect {:host host :port port :username "cmr" :password "cmr"})]
      (debug "Connection started")
      (assoc this :conn conn :running? true)))

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
    (let [conn (:conn this)
          ch (lch/open conn)]
      ;; create the queue
      (debug "Creating queue" queue-name)
      (safe-create-queue ch queue-name {:exclusive false :auto-delete false :durable true})
      ;; create wait queues to use with the primary queue
      (debug "Creating wait queues")
      (doseq [x (range 1 (inc (config/rabbit-mq-max-retries)))
              :let [ttl (* (config/rabbit-mq-ttl-base) (math/expt 4 (dec x)))
                    wq (wait-queue-name queue-name x)
                    _ (debug "Creating wait queue" wq)]]
        (safe-create-queue ch
                           wq
                           {:exclusive false
                            :auto-delete false
                            :durable true
                            :arguments {"x-dead-letter-exchange" default-exchange-name
                                        "x-dead-letter-routing-key" queue-name
                                        "x-message-ttl" ttl}}))
      (rmq/close ch)))

  (publish
    [this queue-name msg]
    (debug "publishing msg:" msg " to queue:" queue-name)
    (let [conn (:conn this)
          ch (lch/open conn)
          payload (json/generate-string msg)
          metadata {:content-type "text/plain" :persistent true}]
      (lcf/select ch)
      (lb/publish ch default-exchange-name queue-name payload metadata)
      (lcf/wait-for-confirms ch)))

  (subscribe
    [this queue-name handler params]
    (let [conn (:conn this)]
      (start-consumer conn queue-name handler params)))

  (message-count
    [this queue-name]
    (let [conn (:conn this)
          ch (lch/open conn)]
      (debug "Getting message count for queue" queue-name " on channel" ch)
      (lq/message-count ch queue-name)))

  (purge-queue
    [this queue-name]
    (let [con (:conn this)
          ch (lch/open conn)]
      (info "Purging all messages from queue" queue-name)
      (lq/purge ch queue-name)))

  (delete-queue
    [this queue-name]
    (let [conn (:conn this)
          ch (lch/open conn)]
      (lq/delete ch queue-name))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-queue
  "Create a RabbitMQConnection"
  [params]
  (let [{:keys [host port username password]} params]
    (->RabbitMQConnection host port username password nil false)))

(comment

  (def q (let [q (create-queue {:host (config/rabbit-mq-host)
                                :port (config/rabbit-mq-port)
                                :username (config/rabbit-mq-username)
                                :passowrd (config/rabbit-mq-password)})]
           (lifecycle/start q {})))

  (defn test-message-handler
    "Handle messages on the queue."
    [ch {:keys [delivery-tag type routing-key] :as meta} ^bytes payload]
    (debug "META:" meta)
    (let [msg (json/parse-string (String. payload) true)
          repeat-count (get msg :repeat-count 0)]
      (debug "Receieved message:" msg)
      (try
        ;; simulate processing with random failures
        ;; will ack after processing so if the consumer dies first the queue will resend

        (if false
          (do
            ;; processed successfully
            (debug "Processed successfully")
            (lb/ack ch delivery-tag))
          (if true
            (if (= repeat-count 5)
              (do
                ;; give up
                (debug "Max retries exceeded for procesessing message:" msg)
                (lb/nack ch delivery-tag false false))
              (let [msg (assoc msg :repeat-count (inc repeat-count))
                    wait-q (wait-queue-name routing-key (inc repeat-count))]
                (debug "Retrying with repeat-count =" (inc repeat-count) " on queue" wait-q)
                (queue/publish q wait-q msg)
                (lb/ack ch delivery-tag)))
            (do
              ;; bad data - nack it
              (debug "Rejecting bad datas")
              (lb/nack ch delivery-tag false false))))


        (catch Throwable e
          (error (.getMessage e))
          ;; Send a rejection to the queue
          (lb/reject ch delivery-tag)))))


  (let [msg {:type :index-concept :concept-id "C1000-PROV1" :revision-id 1}]
    (debug "Creating test queue")
    (queue/create-queue q "test.simple")
    (debug "Subscribing to test queue")
    (queue/subscribe q "test.simple" test-message-handler {})
    (debug "Publishing to test queue")
    (queue/publish q "test.simple" msg))


  (queue/purge-queue q "test.simple")
  (queue/purge-queue q (wait-queue-name "test.simple" 3))
  (queue/message-count q "test.simple")
  (queue/message-count q (wait-queue-name "test.simple" 1))
  (queue/delete-queue q "test.simple")
  (doseq [n (range 1 (inc (config/rabbit-mq-max-retries)))]
    (queue/delete-queue q (wait-queue-name "test.simple" n)))
  )


