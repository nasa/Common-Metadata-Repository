(ns cmr.message-queue.queue.rabbit-mq
  "Implements index-queue functionality using rabbit mq"
  (:gen-class)
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.message_queue.config :as cfg]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.index-queue :as index-queue]
            [cmr.message-queue.data.indexer :as indexer]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]
            [langohr.exchange  :as le]
            [langohr.http :as lhttp]
            [cheshire.core :as json]))

(defn message-metadata
  "Creates a map with the appropriate metadata for our messages"
  [queue-name message-type]
  ;; TODO - Should the :content-type be application/json? Does this matter?
  {:routing-key queue-name :content-type "text/plain" :type message-type :persistent true})

(defn message-handler
  "Handle messages on the indexing queue."
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (println "Got message")
  (let [msg (json/parse-string (String. payload) true)]
    (try
      (indexer/handle-indexing-request type msg)
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
  (lq/declare ch cfg/queue-name {:exclusive false :auto-delete false})
  (lq/bind ch cfg/queue-name cfg/exchange-name {:routing-key cfg/queue-name})
  (lc/subscribe ch cfg/queue-name message-handler {:auto-ack false}))

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
          metadata (message-metadata cfg/queue-name "index-concept")
          msg {:concept-id concept-id :revision-id revision-id}]
      (lb/publish pub-channel cfg/exchange-name cfg/queue-name msg metadata)))

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

(defn create-queue
  "Set up a message consumer with the given number of subscribers"
  [num-subscribers]
  (let [conn (rmq/connect {:host (cfg/rabbit-mq-host) :port (cfg/rabbit-mq-port)})
        pub-channel (lch/open conn)
        ;; create an exchange for our queue using direct routing
        _ (le/declare pub-channel cfg/exchange-name "direct")
        sub-channels (doall (for [_ (range num-subscribers)]
                              (lch/open conn)))]
    (->RabbitMQIndexQueue conn pub-channel sub-channels false)))

(comment
  (let [q (create-queue 4)
        _ (lifecycle/start q {})
        conn (rmq/connect)
        ch (lch/open conn)
        msg (json/generate-string {:concept-id "C1000-PROV1" :revision-id 1})]
    (lb/publish ch cfg/exchange-name cfg/queue-name msg {:routing-key cfg/queue-name :content-type "text/plain" :type "index-concept" :persistent true}))

  )


