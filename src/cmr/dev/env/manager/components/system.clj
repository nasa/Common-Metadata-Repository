(ns cmr.dev.env.manager.components.system
  (:require
    [cmr.dev.env.manager.components.common.process :as process]
    [cmr.dev.env.manager.components.dem.config :as config]
    [cmr.dev.env.manager.components.dem.logging :as logging]
    [cmr.dev.env.manager.components.dem.messaging :as messaging]
    [cmr.dev.env.manager.config :refer [build] :rename {build build-config}]
    [com.stuartsierra.component :as component]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   CMR Service Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cubby
  {:cubby :tbd})

(defn mock-echo
  [builder]
  {:mock-echo (component/using
               (process/create-process-runner-component builder :mock-echo)
               [:config :logging :messaging])})

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
