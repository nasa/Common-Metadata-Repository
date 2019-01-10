(ns cmr.mission-control.components.core
  (:require
    [cmr.exchange.common.components.config :as config]
    [cmr.exchange.common.components.logging :as logging]
    [cmr.mission-control.components.pubsub :as pubsub]
    [cmr.mission-control.config :as config-lib]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Common Configuration Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cfg
  []
  {:config (config/create-component (config-lib/data))})

(def log
  {:logging (component/using
             (logging/create-component)
             [:config])})

(def pubsub
  {:pubsub (component/using
            (pubsub/create-component)
            [:config :logging])})

(defn basic
  []
  (merge (cfg)
         log))

(defn mission-control
  []
  (merge (cfg)
         log
         pubsub))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initializations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-bare-bones
  []
  (component/map->SystemMap (basic)))

(defn initialize
  []
  (component/map->SystemMap (mission-control)))

(def init-lookup
  {:basic #'initialize-bare-bones
   :mission-control #'initialize})

(defn init
  ([]
    (init :mission-control))
  ([mode]
    ((mode init-lookup))))
