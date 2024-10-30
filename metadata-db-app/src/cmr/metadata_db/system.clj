(ns cmr.metadata-db.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.services.cache-info :as cache-info]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common.api.web-server :as web]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :as log]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.message-queue.config :as queue-config]
   [cmr.message-queue.pub-sub :as pub-sub]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.metadata-db.api.routes :as routes]
   [cmr.metadata-db.config :as config]
   [cmr.metadata-db.services.jobs :as mdb-jobs]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.transmit.config :as transmit-config]))

;; Design based on http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches :db :queue-broker :scheduler :unclustered-scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defconfig log-level
  "App logging level"
  {:default "info"})

#_{:clj-kondo/ignore [:unresolved-var]}
(defn create-system
  "Returns a new instance of the whole application."
  ([]
   (create-system "metadata-db"))
  ([connection-pool-name]
   (let [sys {:instance-name (common-sys/instance-name "metadata-db")
              :db (assoc (mdb-util/create-db (config/db-spec connection-pool-name))
                         :result-set-fetch-size
                         (config/result-set-fetch-size))
              :log (log/create-logger-with-log-level (log-level))
              :web (web/create-web-server (transmit-config/metadata-db-port) routes/make-api)
              :nrepl (nrepl/create-nrepl-if-configured (config/metadata-db-nrepl-port))
              :parallel-chunk-size (config/parallel-chunk-size)
              :caches {acl/token-imp-cache-key (acl/create-token-imp-cache)
                       common-health/health-cache-key (common-health/create-health-cache)
                       subscription-cache/subscription-cache-key (subscription-cache/create-cache-client)}
              :scheduler (jobs/create-clustered-scheduler `system-holder :db mdb-jobs/jobs)
              :unclustered-scheduler (jobs/create-scheduler
                                      `system-holder [jvm-info/log-jvm-statistics-job
                                                      (cache-info/create-log-cache-info-job "metadata-db")])
              :queue-broker (queue-broker/create-queue-broker (config/queue-config))
              :sns {:internal (pub-sub/create-topic (queue-config/cmr-internal-subscriptions-topic-name))
                    :external (pub-sub/create-topic (queue-config/cmr-subscriptions-topic-name))}
              :relative-root-url (transmit-config/metadata-db-relative-root-url)}]
     (transmit-config/system-with-connections sys [:access-control :echo-rest]))))

(def start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  (common-sys/start-fn "Metadata DB" component-order))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "Metadata DB" component-order))
