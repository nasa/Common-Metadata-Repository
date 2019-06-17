(ns cmr.message-queue.queue.sqs
  "Implements index-queue functionality using Amazon SQS.
  Note: the terms 'exchange' and 'topic' are used interchangeably in
  comments here. Topics in SNS are (mostly) equivalent to exchanges in RabbitMQ."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.core.async :as a]
   [clojure.string :as string]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.health-helper :as hh]
   [cmr.common.util :as u]
   [cmr.message-queue.config :as config]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]
   [cmr.message-queue.services.queue :as queue])
  (:import
   (clojure.lang Keyword Reflector)
   (cmr.message_queue.test ExitException)
   (com.amazonaws ClientConfiguration)
   (com.amazonaws.auth BasicAWSCredentials)
   (com.amazonaws.auth.policy Condition Policy Principal Resource Statement Statement$Effect)
   (com.amazonaws.auth.policy.actions SQSActions)
   (com.amazonaws.auth.policy.conditions ConditionFactory)
   (com.amazonaws.regions InMemoryRegionImpl)
   (com.amazonaws.services.sns AmazonSNSClient)
   (com.amazonaws.services.sqs AmazonSQSClient)
   (com.amazonaws.services.sns.util Topics)
   (com.amazonaws.services.sqs.model CreateQueueRequest GetQueueUrlRequest GetQueueUrlResult
                                     PurgeQueueRequest ReceiveMessageRequest
                                     SendMessageResult SetQueueAttributesRequest)
   (java.io IOException)
   (java.util ArrayList)
   (java.util HashMap)))

(defconfig queue-polling-timeout
 "Number of seconds SQS should wait before giving up on an attempt to read data from a queue."
 {:default 20
  :type Long})

(defconfig default-queue-visibility-timeout
  "Default number of seconds SQS should wait after a message is read before making it visible to
  other queue readers."
  {:default 300
   :type Long})

(defconfig provider-queue-visibility-timeout
  "Number of seconds SQS should wait after a message is read from a provider queue before making
  it visible to other readers."
  {:default 43200 
   :type Long})

(defconfig default-num-tries
  "Default number of tries (including initial attempt and all retries) for an individual message
  before moving the message to the DLQ."
  {:default 5
   :type Long})

(defconfig sqs-endpoint
  "By default the SQS client will not explicitly set the SQS endpoint. This
  configuration is provided for use in situations where explicitly setting
  the SQS endpoint is desirable or required."
  {:default nil
   :type String})

(defconfig sqs-extend-policy-remaining-exchanges
  "When subscribing a queue to a topic, one may override the AWS policy or
  extend it. CMR's default behaviour for AWS is to not extend the policy
  of the queue that is bound to the first in a set of exchanges, but to do
  so for the remaining exchanges.

  When running SQS/SNS locally, however, to keep policy handling simple, we
  do not want to extend the policies."
  {:default true
   :type Boolean})

(defconfig sns-endpoint
  "By default the SNS client will not explicitly set the SNS endpoint. This
  configuration is provided for use in situations where explicitly setting
  the SNS endpoint is desirable or required."
  {:default nil
   :type String})

(defn queue-visibility-timeout
  "Number of seconds SQS should wait after a message is read from a provider queue before making
  it visible to other readers."
  [queue-name]
  (if (string/includes? queue-name "provider")
    (provider-queue-visibility-timeout)
    (default-queue-visibility-timeout)))

(def queue-arn-attribute
  "String used by the AWS SQS API to identify the attribute containing a queue's
  Amazon Resource Name"
  "QueueArn")

(defn- dead-letter-queue
  "Returns the dead-letter-queue name for a given queue name. The
  given queue name should already be normalized."
  [queue]
  (str queue "_dead_letter_queue"))

(defn- arn->name
  "Convert an Amazon Resource Name (ARN) to a name (topic, queue, etc.)."
  [arn]
  (string/replace arn #".*:" ""))

(defn- http-url->name
  "Convert an Amazon HTTP queue endpoint URL to a queue name."
  [endpoint]
  (last (string/split endpoint #"/")))

(defn- subscription-endpoint->name
  "Given a subscription endpoint (which may be an ARN or an HTTP URL), convert
  it to a queue name."
  [endpoint]
  (debug "Converting endpoint:" endpoint)
  (if (string/starts-with? endpoint "http")
    (http-url->name endpoint)
    (arn->name endpoint)))

(defn- normalize-queue-name
  "Ensure all queues start with gsfc-eosdis-cmr since the permissions in NGAP specify that CMR
  can only create queues with that prefix. Also adds in the environment (sit/uat/wl/ops) and
  replaces dots with underscores. This is needed because SQS only allows alpha-numeric chars
  plus dashes and underscores in queue names, while CMR has dots (periods) in queue names."
  [queue-name]
  (let [prefix (str "gsfc-eosdis-cmr-" (config/app-environment))
        prefix-regex (re-pattern (str "^(" prefix "-)*"))]
    (-> queue-name
        (string/replace "." "_")
        (string/replace "cmr_" "")
        (string/replace prefix-regex (str prefix "-")))))

(defn- -get-topic
  "Returns the Topic with the given display name."
  [sns-client exchange-name]
  (info "Calling SNS to get topic " exchange-name)
  (let [exchange-name (normalize-queue-name exchange-name)
        topics (vec (.getTopics (.listTopics sns-client)))]
    (some (fn [topic]
            (let [topic-arn (.getTopicArn topic)
                  topic-name (arn->name topic-arn)]
              (when (= exchange-name topic-name)
                topic)))
     topics)))

(def get-topic
 "Memoized function that returns the Topic with the given display name."
 (memoize -get-topic))

(defn- get-queue-arn
  "Get the Amazon Resource Name (ARN) for the given queue.
  See http://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html.
  Queue name must be normalized."
  [sqs-client queue-name]
  (let [q-url (.getQueueUrl (.getQueueUrl sqs-client queue-name))
        q-attrs (->> (ArrayList. [queue-arn-attribute])
                     (.getQueueAttributes sqs-client q-url)
                     .getAttributes
                     (into {}))]
   (get q-attrs queue-arn-attribute)))


(defn set-aws-client-attr!
  "Conditionally configure the AWS client instance by calling the given
  method with the given args."
  [client-obj method args]
  (when-not (nil? (first args))
    (debug "\tConfig:" method "=" args)
    (Reflector/invokeInstanceMethod
     client-obj (name method) (object-array args))))

(defn configure-aws-client
  "Configure an AWS service client with values passed pair-wise to the methods
  they are passed with. This function allows for the configuration of an AWS
  service object after instantiation by passing it first an AWS service client
  instance, and then a series of keywords and configuration values, where the
  keywords are spelled the same as the client object's given setter method
  and the configuration values are the results of having called a configuration
  function that will pull a value from the environment or the default, in the
  event of an undefined environment value.

  Example usage:
  ```
  (configure-aws-client
    (new AmazonSQSClient)
    :setEndpoint (sqs-endpoint)
    :setRegion (sqs-region)
    :setTimeOffset (sqs-time-offset)))
  ```

  With the understanding, of course, that the configuration functions in the
  example all have been created.

  Note that since AWS service clients will set service defaults on their own,
  it is best to define the default configuration value as nil, so as to avoid
  unexpected client behaviour."
  [client-obj & args]
  (debug "Configuring client" client-obj)
  (doseq [[config-key config-val] (partition 2 args)]
    (set-aws-client-attr! client-obj config-key [config-val]))
  client-obj)

(defmulti create-aws-client
  "Create an AWS service client, conditionally setting any configured values.

  Note that if the configuration calls (e.g., `(sqs-endpoint)`) return `nil`,
  the configuration operation doesn't actually take place."
  identity)

(defmethod create-aws-client :sqs
  [^Keyword type]
  (configure-aws-client
    (new AmazonSQSClient)
    :setEndpoint (sqs-endpoint)))

(defmethod create-aws-client :sns
  [^Keyword type]
  (configure-aws-client
    (new AmazonSNSClient)
    :setEndpoint (sns-endpoint)))

(defn- create-async-handler
  "Creates a thread that will asynchronously pull messages off the queue, pass them to the handler,
  and process the response. Throwables raised while reading the queue are caught to avoid exiting
  the thread. If an ExitException object is caught it is rethrown to cause the thread to exit - this
  is used during testing to prevent threads from persisting after tests complete."
  ([queue-broker queue-name handler]
   (create-async-handler queue-broker queue-name handler true))
  ([queue-broker queue-name handler auto-reconnect?]
   (info "Starting listener for queue: " queue-name)
   (let [queue-name (normalize-queue-name queue-name)
         sqs-client (get queue-broker :sqs-client)
         queue-url (.getQueueUrl (.getQueueUrl sqs-client queue-name))
         rec-req (doto (ReceiveMessageRequest. queue-url)
                       ;; Only take one message at a time from the queue.
                       (.setMaxNumberOfMessages (Integer. 1))
                       ;; Tell SQS how long to wait before returning with no data (long polling).
                       (.setWaitTimeSeconds (Integer. (queue-polling-timeout))))]
     (a/thread
       (loop [sqs-client-atom (atom sqs-client)]
         (try
           (let [rec-result (.receiveMessage @sqs-client-atom rec-req)]
             (when-let [msg (first (.getMessages rec-result))]
               (let [msg-body (.getBody msg)
                     msg-content (json/decode msg-body true)]
                 (try
                   (handler msg-content)
                   (.deleteMessage @sqs-client-atom queue-url (.getReceiptHandle msg))
                   (catch Throwable e
                     (error e "Message processing failed for message" (pr-str msg) "on queue" queue-name))))))
           ;; Catching this so the next catch block won't - this allows us to exit the thread after a test
           ;; by throwing an ExitException object.
           (catch ExitException e
             (error "Aysnc handler for queue" queue-name "exiting.")
             ;; Catching just to rethrow is generally not a good thing to do, but we want the thread to exit here.
             (throw e))
           (catch Throwable t
             (error t "Async handler for queue" queue-name "continuing after failed message receive.")
             ;; We want to avoid a tight loop in case the call to getMessages is failing immediately.
             (Thread/sleep 1000)
             (when auto-reconnect?
               (warn "Recreating SQS client.")
               (reset! sqs-client-atom (create-aws-client :sqs)))))
         (recur sqs-client-atom))))))

(defn- create-queue
  "Create a queue and its dead-letter-queue if they don't already exist and connect the two."
  [sqs-client queue-name max-tries visibility-timeout]
  (let [q-name (normalize-queue-name queue-name)
        dlq-name (dead-letter-queue q-name)

        ;; Create the dead-letter-queue first and get its url
        dlq-url (.getQueueUrl (.createQueue sqs-client dlq-name))
        dlq-arn (get-queue-arn sqs-client dlq-name)
        create-queue-request (CreateQueueRequest. q-name)
        ;; the policy that sets retries and what dead-letter-queue to use
        redrive-policy (format "{\"maxReceiveCount\":\"%d\", \"deadLetterTargetArn\": \"%s\"}"
                               max-tries
                               dlq-arn)
        ;; create the primary queue
        queue-url (.getQueueUrl (.createQueue sqs-client q-name))
        q-attrs (HashMap. {"RedrivePolicy" redrive-policy
                           "VisibilityTimeout" (str visibility-timeout)})
        set-queue-attrs-request (doto (SetQueueAttributesRequest.)
                                      (.setAttributes q-attrs)
                                      (.setQueueUrl queue-url))]
     (.setQueueAttributes sqs-client set-queue-attrs-request)))

(defn- create-exchange
  "Create an SNS topic to be used as an exchange."
  [sns-client exchange-name]
  (.createTopic sns-client (normalize-queue-name exchange-name)))

(defn- topic-conditions
  "Returns a sequence of Conditions allowing the given exchanges access to a queue.
  These will be applied to an explicit queue later."
  [sns-client exchange-names]
  (map (fn [exchange-name]
           (let [ex-name (normalize-queue-name exchange-name)
                 topic (get-topic sns-client ex-name)
                 topic-arn (.getTopicArn topic)]
            (ConditionFactory/newSourceArnCondition topic-arn)))
      exchange-names))

(defn- sns-to-sqs-access-policy
  "Returns an access policy allowing the given SNS topic to publish to the given SQS queue."
  [sns-client sqs-client queue-name exchange-names]
  (let [conditions (topic-conditions sns-client exchange-names)
        queue-arn (get-queue-arn sqs-client queue-name)
        resource (Resource. queue-arn)
        statement (doto (Statement. Statement$Effect/Allow)
                        (.withPrincipals (into-array Principal [Principal/AllUsers]))
                        (.withActions (into-array SQSActions [SQSActions/SendMessage]))
                        (.withResources (into-array Resource [resource]))
                        (.withConditions (into-array Condition conditions)))
        policy (.withStatements (Policy.) (into-array Statement [statement]))]
    (.toJson policy)))

(defn bind-queue-to-exchange
  "Bind a queue to a single exchange. Extend should be true
  if the queue is already bound to other exchanges."
  [sns-client sqs-client exchange-name queue-name extend?]
  (let [q-name (normalize-queue-name queue-name)
        q-url (.getQueueUrl (.getQueueUrl sqs-client q-name))
        ex-name (normalize-queue-name exchange-name)
        topic (get-topic sns-client ex-name)
        topic-arn (.getTopicArn topic)
        sub-arn (Topics/subscribeQueue sns-client sqs-client topic-arn q-url extend?)]
    ;; use raw mode
    (.setSubscriptionAttributes sns-client sub-arn "RawMessageDelivery" "true")))

(defn- bind-queue-to-exchanges
 "Bind a queue to SNS Topics representing exchanges."
 [sns-client sqs-client exchange-names queue-name]
 (bind-queue-to-exchange sns-client
                         sqs-client
                         (first exchange-names)
                         queue-name
                         false)
 (doseq [exchange-name (rest exchange-names)]
   (bind-queue-to-exchange sns-client
                           sqs-client
                           exchange-name
                           queue-name
                           (sqs-extend-policy-remaining-exchanges))))

(defn- normalized-queue-name->original-queue-name
  "Convert a normalized queue name to the original queue name used to create it."
  [queue-broker queue-name]
  (get-in queue-broker [:normalized-queue-names queue-name] queue-name))

(defn- -get-queue-url
  "Returns the queue url for the given queue name."
  [sqs-client queue-name]
  (info "Calling SQS to get URL for queue " queue-name)
  (.getQueueUrl (.getQueueUrl sqs-client queue-name)))

(def get-queue-url
  "Memoized function that returns the queue url for the given name."
  (memoize -get-queue-url))

(defrecord SQSQueueBroker
  ;; A record containing fields related to accessing SNS/SQS exchanges and queues.
  [
   ;; Connection to AWS SNS
   ^AmazonSNSClient sns-client-atom

   ;; Connection to AWS SQS
   ^AmazonSQSClient sqs-client

   ;; queues known to this broker
   queues

   ;; map of normalized queue names to original queue names - needed for testing with queue broker wrapper
   ;; See normalize-queue-name for an explanation of normalized queue names.
   normalized-queue-names

   ;; exchanges (topics) known to this broker
   exchanges

   ;; a map of queue names to the retry policies for that queue
   queues-to-policies

   ;; a map of queues to sequences of exchange names to which they should be bound
   queues-to-exchanges]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (let [sqs-client (create-aws-client :sqs)
          sns-client (create-aws-client :sns)
          normalized-queue-names (reduce (fn [m queue-name]
                                             (let [nqn (normalize-queue-name queue-name)]
                                               (assoc m nqn queue-name)))
                                         {}
                                         queues)]
      (doseq [queue-name queues]
        (let [max-tries (get-in queues-to-policies [queue-name :max-tries] (default-num-tries))
              visibility-timeout (get-in queues-to-policies [queue-name :visibility-timeout-secs]
                                         (queue-visibility-timeout queue-name))]
          (create-queue sqs-client queue-name max-tries visibility-timeout)))
      (doseq [exchange-name exchanges]
        (create-exchange sns-client exchange-name))
      (doseq [queue (keys queues-to-exchanges)
              :let [exchanges (get queues-to-exchanges queue)]]
        (bind-queue-to-exchanges sns-client sqs-client exchanges queue))
      (assoc this
             :sns-client-atom (atom sns-client)
             :sqs-client sqs-client
             :normalized-queue-names normalized-queue-names)))

  (stop
    [this system]
    (.shutdown @sns-client-atom)
    (.shutdown sqs-client)
    this)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue-protocol/Queue

  (publish-to-queue
    [this queue-name msg]
    (let [msg (json/generate-string msg)
          queue-name (normalize-queue-name queue-name)
          queue-url (get-queue-url sqs-client queue-name)]
      (debug "Publishing message" msg "to queue" queue-name)
      (.sendMessage sqs-client queue-url msg)))

  (get-queues-bound-to-exchange
    [this exchange-name]
    (let [exchange-name (normalize-queue-name exchange-name)
          topic (get-topic @sns-client-atom exchange-name)
          topic-arn (.getTopicArn topic)
          subs (vec (.getSubscriptions (.listSubscriptionsByTopic @sns-client-atom topic-arn)))]
      (map (fn [sub]
               (->> sub
                    .getEndpoint
                    subscription-endpoint->name
                    (normalized-queue-name->original-queue-name this)))
           subs)))

  (publish-to-exchange
    [this exchange-name msg]
    (let [msg (json/generate-string msg)
          exchange-name (normalize-queue-name exchange-name)
          topic (get-topic @sns-client-atom exchange-name)
          topic-arn (.getTopicArn topic)]
      (debug "Publishing message" msg "to exchange" exchange-name)
      (.publish @sns-client-atom topic-arn msg)))

  (subscribe
    [this queue-name handler]
    (let [queue-name (normalize-queue-name queue-name)]
      (create-async-handler this queue-name handler)))

  (reset
    [this]
    (let [sqs-client (:sqs-client this)]
      (doseq [queue (:queues this)
              :let [queue-name (normalize-queue-name queue)
                    dlq-name (dead-letter-queue queue-name)
                    queue-url (.getQueueUrl (.getQueueUrl sqs-client queue-name))
                    dlq-url (.getQueueUrl (.getQueueUrl sqs-client dlq-name))
                    q-purge-req (PurgeQueueRequest. queue-url)
                    dlq-purge-req (PurgeQueueRequest. dlq-url)]]
        (.purgeQueue sqs-client q-purge-req)
        (.purgeQueue sqs-client dlq-purge-req))))

  (reconnect
    [this]
    (warn "Recreating SNS client.")
    (let [sns-client (create-aws-client :sns)]
      (reset! sns-client-atom sns-client)
      this))

  (health
    [this]
    ;; try to get a list of queues for the first topic (exchange) to test the connection to SNS/SQS
    (try
      (queue-protocol/get-queues-bound-to-exchange this (first exchanges))
      {:ok? true}
      (catch Throwable e
        {:ok? false :msg (.getMessage e)}))))

(record-pretty-printer/enable-record-pretty-printing SQSQueueBroker)

(defn create-queue-broker
  "Creates a broker that uses SNS/SQS"
  [{:keys [queues exchanges queues-to-policies queues-to-exchanges]}]
  (->SQSQueueBroker nil nil queues nil exchanges queues-to-policies queues-to-exchanges))

;; Tests to make sure SNS/SQS is working
(comment
  ;; create a broker
  ;; (def broker (lifecycle/start (create-queue-broker {}) nil))
  (def broker (lifecycle/start (create-queue-broker {:queues ["jn_test_queueX5" "jn_test_queueY5" "jn_test_queueZ5"]
                                                     :exchanges ["jn_test_exchangeA5" "jn_test_exchangeB5"]
                                                     :queues-to-exchanges {"jn_test_queueX5" ["jn_test_exchangeA5"]
                                                                           "jn_test_queueY5" ["jn_test_exchangeA5" "jn_test_exchangeB5"]
                                                                           "jn_test_queueZ5" ["jn_test_exchangeB5"]}})
                               nil))

  (def msg-cnt-atom (atom 0))
  ;; list the topics for the cmr-ingest_exchange exchange/topic
  (get-topic (deref (:sns-client-atom broker)) "cmr_ingest_exchange")
  ;; list the queues for the cmr_ingest_exchange
  (queue-protocol/get-queues-bound-to-exchange broker "jn_test_exchangeA5")
  ;; create a test queue
  (create-queue (:sqs-client broker) "jn_test_queue3")
  ;; create a test exchange
  (create-exchange (deref (:sns-client-atom broker)) "jn_test_exchange3")
  (create-exchange (deref (:sns-client-atom broker)) "jn_test_exchange3b")
  ;; list the queues for the cmr_test_exchange
  (queue-protocol/get-queues-bound-to-exchange broker "jn_test_exchangeA5")
  ;; Bind the queue to the new exchange
  (bind-queue-to-exchanges (deref (:sns-client-atom broker)) (:sqs-client broker) ["jn_test_exchangeA5"] "jn_test_queueY5")
  ;; subscribe to test queue with a simple handler that prints received messages
  (queue-protocol/subscribe broker "jn_test_queueY5" (fn [msg] (do (println "MESSAGE!") (swap! msg-cnt-atom inc))))
  ;; publish a message to the queue to verify our subscribe worked
  (queue-protocol/publish-to-queue broker "jn_test_queueY5" {:action "concept-update" :dummy "dummy"})
  ;; publish a message to the exchange to verify the message is sent to the queue
  (queue-protocol/publish-to-exchange broker "jn_test_exchangeA5" {:action "concept-update" :dummy "dummy"}))
