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
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.common.services.health-helper :as hh]])
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
        (info "Message" (pr-str msg) "re-queued with response:" (pr-str (:message resp)))
        (info (format "Retrying with retry-count =%d on queue %s with ttl = %d"
                      (inc retry-count)
                      wait-q
                      ttl))
        (queue/publish queue-broker wait-q msg)
        (lb/ack ch delivery-tag)))))

(defn- message-handler
  "Processes a given message and determines what to do based on the response code returned. Send an
  ack if the message completed successfully. Put the message on a wait queue if the response
  indicates it needs to be retried. Nack the message if the response is marked as failed."
  [queue-broker queue-name client-handler ch metadata ^bytes payload]
  (let [{:keys [delivery-tag routing-key]} metadata
        msg (json/parse-string (String. payload) true)]
    (try
      (let [resp (client-handler msg)]
        (case (:status resp)
          :success (lb/ack ch delivery-tag)
          :retry (attempt-retry queue-broker ch queue-name
                                routing-key msg delivery-tag resp)
          :failure (do
                     ;; bad data - nack it
                     (error (format "Message failed processing with error '%s', it has been removed from the message queue. Message details: %s"
                                    (:message resp)
                                    msg))
                     (lb/nack ch delivery-tag false false))))
      (catch Throwable e
        (error "Message processing failed for message" (pr-str msg) "with error:"
               (.getMessage e))
        (attempt-retry queue-broker ch queue-name routing-key msg delivery-tag
                       {:message (.getMessage e)})))))

(defn- start-consumer
  "Starts a message consumer in a separate thread.

  'queue-broker' is a record that implements
  both the Queue and Lifecycle protocols for interacting with a message queue.

  'queue-name' is the identifier of the queue to use to receive messages and should correpsond to
  the identifier used to create the queue with the create-queue function.

  'client-handler' is a function that takes a single parameter (the message) and attempts to
  process it. This function should respond with a map of the of the follwing form:
  {:status status :message message}
  where status is one of (:success, :retry, :failure) and message is optional.

  'params' is a map containing queue implementation specific settings, if necessary. Currently the
  only supported setting for RabbitMQ is :prefetch, which corresponds to the number of messages
  a consumer should pull off the queue at one time."
  [queue-broker queue-name client-handler params]
  (let [{:keys [prefetch]} params
        conn (:conn queue-broker)
        ;; don't want this channel to close automatically, so no with-channel here
        sub-ch (lch/open conn)
        handler (partial message-handler queue-broker queue-name client-handler)]
    ;; By default, RabbitMQ uses a round-robin approach to send messages to listeners. This can
    ;; lead to stalls as workers wait for other workers to complete. Setting QoS (prefetch) to 1
    ;; prevents this.
    (lb/qos sub-ch (or prefetch 1))
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
          this (assoc this :conn conn :running? true)]
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
    (with-channel
      [ch conn]
      ;; create the queue
      ;; see http://reference.clojurerabbitmq.info/langohr.queue.html
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
                                 "x-message-ttl" ttl}}))))

  (publish
    [this queue-name msg]
    (let [payload (json/generate-string msg)
          metadata {:content-type "application/json" :persistent true}]
      (with-channel
        [pub-ch conn]
        ;; put channel into confirmation mode
        (lcf/select pub-ch)

        ;; publish the message
        (lb/publish pub-ch default-exchange-name queue-name payload metadata)
        ;; block until the confirm arrives or return false if queue nacks the message
        (lcf/wait-for-confirms pub-ch))))

  (subscribe
    [this queue-name handler params]
    (start-consumer this queue-name handler params))

  (message-count
    [this queue-name]
    (with-channel [ch conn]
                  (lq/message-count ch queue-name)))

  (reset
    [this]
    (debug "Resetting RabbitMQ")
    (doseq [queue-name persistent-queues]
      (purge-queue this queue-name))
    )
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-queue-broker
  "Create a RabbitMQBroker"
  [params]
  (let [{:keys [host port admin-port username password queues]} params]
    (->RabbitMQBroker host port admin-port username password nil queues false)))


(defn health-fn
  "Check that the queue is up and responding"
  [context]
  (try
    (let [queue-broker (get-in context [:system :queue-broker])
          ;; need to deal with broker-wrapper
          queue-broker (if (instance? RabbitMQBroker queue-broker)
                         queue-broker
                         (:queue-broker queue-broker))
          {:keys [host admin-port username password]} queue-broker
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

(defn health
  "Check RabbitMQ health with timeout handling."
  [context]
  (let [timeout-ms (* 1000 (+ 2 (hh/health-check-timeout-seconds)))]
    (hh/get-health #(health-fn context) timeout-ms)))


(comment
  (do
    (def q (let [q (create-queue-broker {:host (config/rabbit-mq-host)
                                         :port (config/rabbit-mq-port)
                                         :admin-port (config/rabbit-mq-admin-port)
                                         :username (config/rabbit-mq-user)
                                         :password (config/rabbit-mq-password)
                                         :queues ["test.simple"]
                                         :ttls [1 1 1 1 1]})]
             (lifecycle/start q {})))

    (health {:system {:queue-broker q}})

    (defn random-message-handler
      [msg]
      (info "Test handler")
      (let [val (rand)
            rval (cond
                   (> 0.5 val) {:status :success}
                   (> 0.97 val) {:status :retry :message "service down"}
                   :else {:status :failure :message "bad data"})]

        rval))

    (defn sleep-success-message-handler
      [msg]
      (let [sleep-secs 2]
        (Thread/sleep (* 1000 sleep-secs))
        ; (info "Test handler - finished sleeping")
        {:status :success}))

    (queue/subscribe q "test.simple" sleep-success-message-handler {})


    (doseq [n (range 0 1000)
            :let [concept-id (str "C" n "-PROV1")
                  msg {:action :index-concept
                       :concept-id concept-id
                       :revision-id 1}]]
      (queue/publish q "test.simple" msg))

    (info "Finished publishing messages for processing"))


  (queue/create-queue q "cmr_index.queue")

  (queue/message-count q "test.simple")

  (delete-queue q "test.simple")

  (delete-queue q "cmr_index.queue")

  (purge-queue q "cmr_index.queue")

  (lifecycle/stop q {})

  )