(ns cmr.dev.env.manager.components.system
  (:require
    [cmr.dev.env.manager.components.config :as config]
    [cmr.dev.env.manager.components.logging :as logging]
    [cmr.dev.env.manager.components.messaging :as messaging]
    [cmr.dev.env.manager.components.subscribers :as subscribers]
    [cmr.dev.env.manager.components.timer :as timer]
    [cmr.dev.env.manager.components.watcher :as watcher]
    [cmr.dev.env.manager.config :refer [memoized-build]
                                :rename {memoized-build build-config}]
    [cmr.process.manager.components.docker :as docker]
    [cmr.process.manager.components.process :as process]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Constants, Data Structures, & Functions   ;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def log-subscriber
  {:fatal (fn [msg] (log/fatal msg))
   :error (fn [msg] (log/error msg))
   :warn (fn [msg] (log/warn msg))
   :info (fn [msg] (log/info msg))
   :debug (fn [msg] (log/debug msg))
   :trace (fn [msg] (log/trace msg))
   :timer (fn [msg] (log/tracef "The %s interval has passed." (:interval msg)))
   :file-event (fn [msg] (log/debug "File system got watcher event:" msg))})

(def default-subscribers
  [{:topic :fatal :fn (:fatal log-subscriber)}
   {:topic :error :fn (:error log-subscriber)}
   {:topic :warn :fn (:warn log-subscriber)}
   {:topic :info :fn (:info log-subscriber)}
   {:topic :debug :fn (:debug log-subscriber)}
   {:topic :trace :fn (:trace log-subscriber)}])

(def default-timer-subscribers
  [{:interval :all :fn (:timer log-subscriber)}])

(def default-watcher-subscribers
  [{:topic :file-event :fn (:file-event log-subscriber)}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   D.E.M Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cfg
  [builder]
  {:config (config/create-component builder)})

(def log
  {:logging (component/using
             (logging/create-component)
             [:config])})

(def msg
  {:messaging (component/using
               (messaging/create-component)
               [:config :logging])})

(def sub
  {:subscribers (component/using
                 (subscribers/create-component
                  default-subscribers)
                 [:config :logging :messaging])})

(defn watch
  [builder]
  {:watcher (component/using
             (watcher/create-component
              builder
              default-watcher-subscribers)
             [:config :logging :messaging :subscribers])})

(defn tmr
  [builder]
  {:timer (component/using
           (timer/create-component
            builder
            config/timer-delay
            default-timer-subscribers)
           [:config :logging :messaging :subscribers])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support Service Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn elastic-search
  [builder]
  {:elastic-search (component/using
                    (docker/create-component
                      builder
                      :elastic-search
                      config/elastic-search-opts)
                    [:config :logging :messaging :subscribers])})

(defn elastic-search-head
  [builder]
  {:elastic-search-head (component/using
                         (docker/create-component
                           builder
                           :elastic-search-head
                           config/elastic-search-head-opts)
                         [:config :logging :messaging :subscribers
                          :elastic-search])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   CMR Service Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mock-echo
  [builder]
  {:mock-echo (component/using
               (process/create-component builder :mock-echo)
               [:config :logging :messaging :subscribers])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Intilizations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-default
  ([]
    (initialize-default build-config))
  ([config-builder]
    (component/map->SystemMap
      (merge (cfg config-builder)
             log
             msg
             sub
             (watch config-builder)
             (tmr config-builder)
             (elastic-search config-builder)
             (elastic-search-head config-builder)
             (mock-echo config-builder)))))

(defn initialize-bare-bones
  ([]
    (initialize-bare-bones build-config))
  ([config-builder]
    (component/map->SystemMap
      (merge (cfg config-builder)
             log))))


(def init
  {:default initialize-default
   :basic initialize-bare-bones})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Managment Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def stop #'component/stop)

(defn start
  ([config-builder]
   (start (initialize-default config-builder)))
  ([config-builder system-type]
   (case system-type
     :web :tbd
     :basic (component/start (initialize-bare-bones config-builder))
     :repl (component/start (initialize-default config-builder))
     :cli :tbd)))

(defn restart
  ([system]
   (-> system
       (component/stop)
       (component/start))))
