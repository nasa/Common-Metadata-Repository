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
  "Returns the name of the wait queue associated with the given queue name and ttl"
  [queue-name ttl]
  (str queue-name "-wait-" ttl))

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
    (debug "Setting channel prefect")
    (lb/qos ch (or prefetch 1))
    (lc/subscribe ch queue-name handler {:auto-ack false})))

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

  ; (index-concept
  ;   [this concept-id revision-id]
  ;   ;; add a request to the index queue
  ;   (let [pub-channel (:publisher-channel this)
  ;         metadata (message-metadata cfg/queue-name "index-concept")
  ;         msg {:concept-id concept-id :revision-id revision-id}]
  ;     (lb/publish pub-channel cfg/exchange-name cfg/queue-name msg metadata)))

  (create-queue
    [this queue-name]
    (let [{:keys [conn]} this
          ch (lch/open conn)]
      ;; create the queue
      (debug "Creating queue" queue-name)
      (lq/declare ch queue-name {:exclusive false :auto-delete false})
      ;; create wait queues to use with the primary queue
      (debug "Creating wait queues")
      (for [x [1 2 3 4 5]
            :let [ttl (math/expt 5000 x)]]
        (lq/declare ch
                    (wait-queue-name queue-name ttl)
                    {:exclusive false
                     :auto-delete false
                     :arguments {"x-dead-letter-exchange" queue-name
                                 "x-message-ttl" ttl}}))
      (rmq/close ch)))

  (publish
    [this queue-name msg metadata]
    (let [ch (lch/open conn)
          payload (json/generate-string msg)]
      (lcf/select ch)
      (lb/publish ch default-exchange-name queue-name payload metadata)
      (lcf/wait-for-confirms ch)))

  (subscribe
    [this queue-name handler params]
    (let [conn (:conn this)]
      (start-consumer conn queue-name handler params))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-queue
  "Create a RabbitMQConnection"
  [params]
  (let [{:keys [host port username password]} params]
    (->RabbitMQConnection host port username password nil false)))

(comment

  (defn test-message-handler
    "Handle messages on the queue."
    [ch {:keys [delivery-tag type] :as meta} ^bytes payload]
    (let [msg (json/parse-string (String. payload) true)]
      (try
        ;;
        (debug "Receieved message:" msg)
        (catch Throwable e
          (error (.getMessage e))
          ;; Send a rejection to the queue
          (lb/reject ch delivery-tag)))))


  (let [q (create-queue {:host (config/rabbit-mq-host)
                         :port (config/rabbit-mq-port)
                         :username (config/rabbit-mq-username)
                         :passowrd (config/rabbit-mq-password)})
        q (lifecycle/start q {})
        msg {:concept-id "C1000-PROV1" :revision-id 1}]
    (debug "Creating test queue")
    (queue/create-queue q "test.simple")
    (debug "Subscribing to test queue")
    (queue/subscribe q "test.simple" test-message-handler {})
    (debug "Publishing to test queue")
    (queue/publish q "test.simple" msg {:content-type "text/plain" :type "index-concept" :persistent true}))

  )


