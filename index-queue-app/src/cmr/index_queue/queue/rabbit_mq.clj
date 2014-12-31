(ns cmr.index-queue.queue.rabbit-mq
  "Implements index-queue functionality using rabbit mq"
  (:gen-class)
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.config :as cfg]
            [cmr.common.services.errors :as errors]
            [cmr.index-queue.queue.index-queue :as index-queue]
            [cmr.index-queue.data.indexer :as indexer]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]
            [langohr.exchange  :as le]
            [cheshire.core :as json]))

(def exchange-name
  "The name of the queue exchange to use to retrieve messages"
  (cfg/config-value :indexer-queue-exchange "indexer.exchange"))

(def queue-name
  "The name of the queue to use to retrieve messages"
  (cfg/config-value :indexer-queue-name "indexer.queue"))

(def queue-channel-count
  "The number of channels to use to retreive messgages. There should be one channel
  per worker."
  (cfg/config-value :queue-channel-count 4))

(defn message-metadata
  "Creates a map with the appropriate metadata for our messages"
  [queue-name message-type]
  ;; TODO - Should the :content-type be application/json? Does this matter?
  {:routing-key queue-name :content-type "text/plain" :type message-type :persistent true})

(defmulti handle-indexing-request
  "Handles indexing requests received from the message queue."
  (fn [request-type msg]
    (keyword request-type)))

(defmethod handle-indexing-request :index-concept
  [request-type msg]
  (let [{:keys [concept-id revision-id]} msg]
    (debug "Received index-concept request for concept-id" concept-id "revision-id" revision-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :delete-concept
  [request-type msg]
  (let [{:keys [concept-id revision-id]} msg]
    (debug "Received delete-concept request for concept-id" concept-id "revision-id" revision-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :re-index-provider
  [request-type msg]
  (let [{:keys [provider-id]} msg]
    (debug "Received re-index-provider request for provider-id" provider-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :delete-provider
  [request-type msg]
  (let [{:keys [provider-id]} msg]
    (debug "Received delete-provider request for provider-id" provider-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :default
  [request-type _]
  (errors/internal-error! (str "Received unknown message type: " request-type)))

(defn message-handler
  "Handle messages on the indexing queue."
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (let [msg (json/parse-string (String. payload) true)]
    (try
      (handle-indexing-request type msg)
      ;; Acknowledge message
      (lb/ack ch delivery-tag)
      (catch Throwable e
        (error (.getMessage e))
        ;; Send a rejection to the queue
        (lb/reject ch delivery-tag)))))

(defn start-consumer
  "Starts an index message consumer bound to the index exchange in a separate thread"
  [ch]
  ;; By default, RabbitMQ uses a round-robin approach to send messages to listeners. This can
  ;; lead to stalls as workers wait for other workers to complete. Setting QoS (prefetch) to 1
  ;; prevents this.
  (lb/qos ch 1)
  (lq/declare ch queue-name {:exclusive false :auto-delete false})
  (lq/bind ch queue-name exchange-name {:routing-key queue-name})
  (lc/subscribe ch queue-name message-handler {:auto-ack false}))

(defrecord RabbitMQIndexQueue
  [
   ;; Connection to the message queue
   conn

   ;; The channel used to publish messages
   publisher-channel

   ;; A list of the channels used by the listeners/workers
   subscriber-channels

   ;; true or false to indicate it's running
   running?
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (info "Starting queue workers")
    (when (:running? this)
      (errors/internal-error! "MessageConsumer already running"))
    (if-let [channels (:subscriber-channels this)]
      (do
        (doseq [ch channels]
          (start-consumer ch))
        (assoc this :running? true))
      (errors/internal-error! "No channels to consume")))

  (stop
    [this system]
    (when (:running? this)
      ;; close all the channels and then the connection
      (doseq [ch (:subscriber-channels this)]
        (rmq/close ch))
      (rmq/close (:publisher-channel this))
      (rmq/close conn)
      (assoc this :running? false)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  index-queue/IndexQueue

  (index-concept
    [this concept-id revision-id]
    ;; add a request to the index queue
    (let [pub-channel (:publisher-channel this)
          metadata (message-metadata queue-name "index-concept")
          msg {:concept-id concept-id :revision-id revision-id}]
      (lb/publish pub-channel exchange-name queue-name msg metadata)))

  (delete-concept-from-index
    [this concept-id revision-id]
    "Remove the given concept revision")

  (delete-provider-from-index
    [This provider-id]
    "Remove a provider and all its concepts from the index")

  (reindex-provider-collections
    [this provider-id]
    "Reindexes all the concepts for the given provider"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-index-queue
  "Set up a message consumer with the given channels"
  [num-worker-channels]
  (let [conn (rmq/connect)
        pub-channel (lch/open conn)
        ;; create an exchange for our queue using direct routing
        _ (le/declare pub-channel exchange-name "direct")
        sub-channels (doall (for [_ (range num-worker-channels)]
                              (lch/open conn)))]
    (->RabbitMQIndexQueue conn pub-channel sub-channels false)))

(comment
  (let [conn (rmq/connect)
        ch (lch/open conn)
        msg (json/generate-string {:concept-id "C1000-PROV1" :revision-id 1})]
    (lb/publish ch exchange-name queue-name msg {:routing-key queue-name :content-type "text/plain" :type "index-concept" :persistent true}))


  )


