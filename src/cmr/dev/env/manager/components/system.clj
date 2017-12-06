(ns cmr.dev.env.manager.components.system
  (:require
    [cmr.dev.env.manager.components.common.docker :as docker]
    [cmr.dev.env.manager.components.common.process :as process]
    [cmr.dev.env.manager.components.dem.config :as config]
    [cmr.dev.env.manager.components.dem.logging :as logging]
    [cmr.dev.env.manager.components.dem.messaging :as messaging]
    [cmr.dev.env.manager.components.dem.subscribers :as subscribers]
    [cmr.dev.env.manager.config :refer [build elastic-search-opts]
                                :rename {build build-config}]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Constants, Data Structures, & Functions   ;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn log-subscriber
  [logging-type]
  (case logging-type
    :fatal (fn [content] (log/fatal content))
    :error (fn [content] (log/error content))
    :warn (fn [content] (log/warn content))
    :info (fn [content] (log/info content))
    :debug (fn [content] (log/debug content))
    :trace (fn [content] (log/trace content))))

(def default-subscribers
  [{:topic :fatal :fn (log-subscriber :fatal)}
   {:topic :error :fn (log-subscriber :error)}
   {:topic :warn :fn (log-subscriber :warn)}
   {:topic :info :fn (log-subscriber :info)}
   {:topic :debug :fn (log-subscriber :debug)}
   {:topic :trace :fn (log-subscriber :trace)}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   D.E.M Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cfg
  [builder]
  {:config (config/create-config-component builder)})

(def log
  {:logging (component/using
             (logging/create-logging-component)
             [:config])})

(def msg
  {:messaging (component/using
               (messaging/create-messaging-component)
               [:config :logging])})

(def sub
  {:subscribers (component/using
                 (subscribers/create-subscribers-component
                  default-subscribers)
                 [:config :logging :messaging])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support Service Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn elastic-search
  [builder]
  {:elastic-search (component/using
                    (docker/create-runner-component
                      builder
                      :elastic-search
                      elastic-search-opts)
                    [:config :logging :messaging :subscribers])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   CMR Service Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cubby
  {:cubby :tbd})

(defn mock-echo
  [builder]
  {:mock-echo (component/using
               (process/create-runner-component builder :mock-echo)
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
             (elastic-search config-builder)
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
