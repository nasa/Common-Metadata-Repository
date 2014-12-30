(ns cmr.index-queue.services.callback_service
  "Defines callbacks to handle indexing request messages from the queue"
  (:gen-class)
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
    				[cmr.common.config :as cfg]
        		[cmr.common.services.errors :as errors]
    				[langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]))

(def exchange-name
  "The name of the queue exchange to use to retrieve messages"
  (cfg/config-value :indexer-queue-exchange ""))

(def queue-name
  "The name of the queue to use to retrieve messages"
  (cfg/config-value :indexer-queue-name "indexer.queue"))

(def queue-channel-count
  "The number of channels to use to retreive messgages. There should be one channel
  per worker."
  (cfg/config-value :queue-channel-count 4))

(defmulti handle-indexing-request
  "Handles indexing requests received from the message queue."
  (fn [request-type ^bytes payload]
    (keyword request-type)))

(defmethod handle-indexing-request :index-concept
  [request-type ^bytes payload]
  (println (str "Received index-concept request: " (String. payload "UTF-8"))))

(defmethod handle-indexing-request :delete-concept
  [request-type ^bytes payload]
  (println (str "Received message: " (String. payload "UTF-8"))))

(defmethod handle-indexing-request :index-provider
  [request-type ^bytes payload]
  (println (str "Received message: " (String. payload "UTF-8"))))

(defmethod handle-indexing-request :default
  [request-type ^bytes payload]
  (println (str "request-type: " request-type))
  (println (str "Received message: " (String. payload "UTF-8"))))

(defn message-handler
  "Handle messages on the indexing queue."
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (println meta)
  (try
    (handle-indexing-request type payload)
    ;; Acknowledge message
    (lb/ack ch delivery-tag)
    (catch Throwable e
      ;; Send a rejection to the queue
      (lb/reject ch delivery-tag))))

(defn start-consumer
  "Starts an index message consumer bound to the index exchange in a separate thread"
  [ch]
  ;; By default, RabbitMQ uses a round-robin approach to send messages to listeners. This can
  ;; lead to stalls as workers wait for other workers to complete. Setting QoS (prefetch) to 1
  ;; prevents this.
  (lb/qos ch 1)
  (lq/declare ch queue-name {:exclusive false :auto-delete false})
  ;(lq/bind ch queue-name exchange-name)
  (lc/subscribe ch queue-name message-handler {:auto-ack false}))

(defn create-queue-channels
  "Creates channels for the indexing queue"
  [num-channels]
  (let [conn (rmq/connect)]
    (for [n num-channels]
      (lch/open conn))))

(defrecord MessageConsumer
  [
   ;; Connection to the message queue
   conn

   ;; A list of the open channels
   channels

   ;; true or false to indicate it's running
   running?
   ]

  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting queue workers")
    (when (:running? this)
      (errors/internal-error! "MessageConsumer already running"))
    (if-let [channels (:channels this)]
      (do
        (doseq [ch channels]
          (start-consumer ch))
        (assoc this :running? true))
      (errors/internal-error! "No channels to consume")))

  (stop
    [this system]
    (when (:running? this)
      ;; close all the channels and then the connection
      (doseq [ch (:channels this)]
        (rmq/close ch))
      (rmq/close conn)
      (assoc this :running? false))))

(defn create-message-consumer
  "Set up a message consumer with the given channels"
  [num-channels]
  (let [conn (rmq/connect)
        channels (doall (for [_ (range num-channels)]
                          (lch/open conn)))]

    (->MessageConsumer conn channels false)))


(comment
  (let [conn (rmq/connect)
        ch (lch/open conn)]
    (lb/publish ch "" queue-name "Hi!" {:content-type "text/plain" :type "index-concept"}))


  )

