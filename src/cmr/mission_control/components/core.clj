(ns cmr.mission-control.components.core
  (:require
    [cmr.mission-control.components.config :as config]
    [cmr.mission-control.components.logging :as logging]
    [cmr.mission-control.components.pubsub :as pubsub]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Common Configuration Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cfg
  [cfg-data]
  {:config (config/create-component cfg-data)})

(def log
  {:logging (component/using
             (logging/create-component)
             [:config])})

(def pubsub
  {:pubsub (component/using
            (pubsub/create-component)
            [:config :logging])})

(defn basic
  [cfg-data]
  (merge (cfg cfg-data)
         log))

(defn mission-control
  [cfg-data]
  (merge (basic cfg-data)
         pubsub))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initializations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-bare-bones
  []
  (-> (config/build-config)
      basic
      component/map->SystemMap))

(defn initialize
  []
  (-> (config/build-config)
      mission-control
      component/map->SystemMap))

(def init-lookup
  {:basic #'initialize-bare-bones
   :mission-control #'initialize})

(defn init
  ([]
    (init :mission-control))
  ([mode]
    ((mode init-lookup))))
