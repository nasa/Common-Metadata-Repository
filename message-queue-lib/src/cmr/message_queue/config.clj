(ns cmr.message-queue.config
  (:require
   [cheshire.core :as json]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.services.errors :as errors]))

(declare app-environment)
(defconfig app-environment
  "The environment in which the application is running in NGAP (wl, sit, uat, ops)"
  {:default "local"})

(declare time-to-live-s)
(defconfig time-to-live-s
  "The Time-To-Live (TTL) for each retry queue (in seconds)."
  {:default [5, 50, 500, 5000, 50000]
   :parser #(json/decode ^String %)})

(declare publish-queue-timeout-ms set-publish-queue-timeout-ms!)
(defconfig publish-queue-timeout-ms
  "Number of milliseconds to wait for a publish request to be confirmed before considering the
  request timed out."
  {:default 10000 :type Long})

(declare queue-type)
(defconfig queue-type
  "This indicates which type of queue to use. Valid types are \"memory\",
  and \"aws\""
  {:default "memory"})

(declare messaging-retry-delay)
(defconfig messaging-retry-delay
  "This configuration value is used to determine how long to wait before
  retrying a messaging operation."
  {:default 2000 :type Long})

(defn default-config
  "Returns a default config map for connecting to the message queue"
  []
  {:requested-heartbeat 120
   :queues []
   :exchanges []
   :queues-to-exchanges {}})

(defn merge-configs
  "Takes two message queue configs and merges them. Throws an exception if they contain conflicting
  infomation"
  [config1 config2]
  (let [must-match-keys [:host :port :admin-port :username :password :requested-heartbeat]]
    (when-not (apply = (map #(select-keys % must-match-keys) [config1 config2]))
      (errors/internal-error! "Configs contained conflicting information"))
    (-> config1
        (update-in [:queues] #(distinct (concat % (:queues config2))))
        (update-in [:exchanges] #(distinct (concat % (:exchanges config2))))
        (update-in [:queues-to-exchanges]
                   (fn [q-to-e]
                     (merge-with
                       (fn [v1 v2]
                         (if (= v1 v2)
                           v1
                           (errors/internal-error!
                             (format "Queue was mapped to two different exchange sets: %s %s"
                                     (pr-str v1) (pr-str v2)))))
                       q-to-e
                       (:queues-to-exchanges config2))))
        (update-in [:queues-to-policies] merge (:queues-to-policies config2)))))

(declare cmr-internal-subscriptions-topic-name)
(defconfig cmr-internal-subscriptions-topic-name
  "The name of the internal subscriptions topic."
  {:default (str "cmr-internal-subscriptions-" (app-environment))})

(declare cmr-subscriptions-topic-name)
(defconfig cmr-subscriptions-topic-name
  "The name of the subscriptions topic to which external clients can subscribe."
  {:default (str "cmr-subscriptions-" (app-environment))})

(declare cmr-internal-subscriptions-queue-name)
(defconfig cmr-internal-subscriptions-queue-name
  "The name of the internal subscriptions queue."
  {:default (str "cmr-internal-subscriptions-queue-" (app-environment))})

(declare cmr-internal-subscriptions-dead-letter-queue-name)
(defconfig cmr-internal-subscriptions-dead-letter-queue-name
  "The name of the internal subscriptions queue."
  {:default (str "cmr-internal-subscriptions-dead-letter-queue-" (app-environment))})

(declare cmr-subscriptions-dead-letter-queue-name)
(defconfig cmr-subscriptions-dead-letter-queue-name
  "The name of the subscriptions dead letter queue for external subscriptions."
  {:default (str "cmr-subscriptions-dead-letter-queue-" (app-environment))})

(declare sqs-server-protocol)
(defconfig sqs-server-protocol
  "Default SQS server host."
  {:default "http"})

(declare sqs-server-host)
(defconfig sqs-server-host
  "Default SQS server host."
  {:default "localhost"})

(declare sqs-server-port)
(defconfig sqs-server-port
  "Default SQS server host."
  {:default "9324"})

(declare sqs-server-url)
(defconfig sqs-server-url
  "The URL of the local or test SQS implementation."
  {:default (str (sqs-server-protocol)
                 "://"
                 (sqs-server-host)
                 ":"
                 (sqs-server-port))})
