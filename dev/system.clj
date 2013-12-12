(ns system
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojure.tools.logging :refer [info error]]
            [embedded-elastic-server :as elastic-server]
            [elastic-connection]))


(defrecord Config
  [
   ;; The HTTP Port to listen on for the web server
   port

   ;; The numeric id to use as an identifier for the app. Used to generate unique ids.
   worker-id

   ;; The logging config
   log
   ])

(def default-config {:http-port 9234
                     :transport-port 9345})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:elastic-server :elastic-connection])

(defn create-system
  "Returns a new instance of the whole application."
  [config]
  {:config config
   :elastic-server (elastic-server/create-server)
   :elastic-connection (elastic-connection/create-connection)})

(defn- try-start-component
  "Attempts to start the component. If an error occurs it is logged and the unstarted component is
  returned."
  [component system]
  (try
    (lifecycle/start component system)
    (catch Throwable e
      (error e "Error starting component.")
      component)))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(try-start-component % system)))
                               this
                               component-order)]
    (info "System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/stop % system)))
                               this
                               (reverse component-order))]
    (info "System stopped")
    stopped-system))
