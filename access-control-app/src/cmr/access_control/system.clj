(ns cmr.access-control.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.nrepl :as nrepl]
            [cmr.transmit.config :as transmit-config]
            [cmr.access-control.api.routes :as routes]
            [cmr.access-control.data.access-control-index :as access-control-index]
            [cmr.common-app.services.search.elastic-search-index :as search-index]
            [cmr.access-control.config :as config]
            [cmr.message-queue.queue.rabbit-mq :as rmq]
            [cmr.common.api.web-server :as web]
            [cmr.acl.acl-fetcher :as af]
            [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
            [cmr.common.jobs :as jobs]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.access-control.services.event-handler :as event-handler]
            [cmr.common-app.system :as common-sys]))

(defconfig access-control-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig access-control-public-protocol
  "The protocol to use in documentation examples for the access-control application."
  {:default "http"})

(def public-conf
  "Public access-control configuration used for generating example requests in documentation"
  {:protocol (access-control-public-protocol)
   :relative-root-url (transmit-config/access-control-relative-root-url)})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :caches :search-index :queue-broker :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :search-index (search-index/create-elastic-search-index)
             :web (web/create-web-server (transmit-config/access-control-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (access-control-nrepl-port))
             :queue-broker (rmq/create-queue-broker (config/rabbit-mq-config))
             :caches {af/acl-cache-key (af/create-acl-cache
                                        (stl-cache/create-single-thread-lookup-cache)
                                        [:system-object :provider-object :single-instance-object])}
             :public-conf public-conf
             :relative-root-url (transmit-config/access-control-relative-root-url)
             :scheduler (jobs/create-scheduler
                         `system-holder
                         [(af/refresh-acl-cache-job "access-control-acl-cache-refresh")])}]
    (transmit-config/system-with-connections sys [:echo-rest :metadata-db :urs])))

(defn start
  "Performs side effects to initialize the system, acquire resources,  and start it running. Returns
  an updated instance of the system."
  [system]
  (info "Access Control system starting")
  (let [started-system (common-sys/start system component-order)]
    (when (:queue-broker system)
      (event-handler/subscribe-to-events {:system started-system}))

    (info "Access control system started")
    started-system))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "access-control" component-order))

(defn dev-start
  "Starts the system but performs extra calls to make sure all indexes are created in elastic."
  [system]
  (let [started-system (start system)]
    (try
      (access-control-index/create-index-or-update-mappings (:search-index started-system))
      (catch Exception e
        (.printStackTrace e)
        (common-sys/stop started-system component-order)))
    started-system))

