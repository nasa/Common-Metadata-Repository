(ns hxgm30.event.components.core
  (:require
    [com.stuartsierra.component :as component]
    [hxgm30.event.components.config :as config]
    [hxgm30.event.components.logging :as logging]
    [hxgm30.event.components.pubsub :as pubsub]
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

(defn event-system
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
      event-system
      component/map->SystemMap))

(def init-lookup
  {:basic #'initialize-bare-bones
   :event-system #'initialize})

(defn init
  ([]
    (init :event-system))
  ([mode]
    ((mode init-lookup))))
