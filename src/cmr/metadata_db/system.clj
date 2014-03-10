(ns cmr.metadata-db.system
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [taoensso.timbre :as timbre
             :refer (trace debug info warn error fatal spy)]))

;; Design based on http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:web])

(def default-log-config
  ; The level to log out
  {:level :debug
   ;; The path to the file to log to
   :file "log/metadata-db.log"
   :stdout-enabled true})


(defrecord Config
  [
   ;; The HTTP Port to listen on for the web server
   port

   ;; The logging config
   log
   ])

(defn create-system
  "Returns a new instance of the whole application."
  [db web]
  {:db db :web web})

(defn- setup-logging [config]
  (let [log-config (get config :log default-log-config)]
    (timbre/set-level! (or (:level log-config) :warn))
    (timbre/set-config! [:timestamp-pattern] "yyyy-MM-dd HH:mm:ss")

    ; Enable file logging
    (timbre/set-config! [:appenders :spit :enabled?] true)
    (timbre/set-config! [:shared-appender-config :spit-filename]
                        (:file log-config))

    ;; Log current thread.
    (timbre/set-config! [:prefix-fn] ; A deprecated config option but I couldn't get it to work with :fmt-output-fn
                        (fn [{:keys [level timestamp hostname ns]}]
                          (str timestamp " " hostname " " (.getId (Thread/currentThread)) " " (-> level name string/upper-case)
                               " [" ns "]")))

    ; Enable/disable stdout logs
    (timbre/set-config! [:appenders :standard-out :enabled?]
                        (:stdout-enabled log-config))))


(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (setup-logging (:config this))
  (info "System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/start % system)))
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
