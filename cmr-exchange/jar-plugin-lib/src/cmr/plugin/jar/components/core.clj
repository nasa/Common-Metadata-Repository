(ns cmr.plugin.jar.components.core
  (:require
    [cmr.exchange.common.components.config :as config]
    [cmr.exchange.common.components.logging :as logging]
    [cmr.plugin.jar.components.registry :as registry]
    [cmr.plugin.jar.config :as config-lib]
    [com.stuartsierra.component :as component]))

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

(def reg
  {:plugin (component/using
             (registry/create-component)
             [:config :logging])})

;;; Additional components for systems that want to supress logging (e.g.,
;;; systems created for testing).

(def reg-without-logging
  {:pubsub (component/using
            (registry/create-component)
            [:config])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initializations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-config-only
  []
  (component/map->SystemMap (cfg)))

(defn initialize-bare-bones
  []
  (component/map->SystemMap
    (merge (cfg)
           log)))

(defn initialize-without-logging
  []
  (component/map->SystemMap
    (merge (cfg)
           reg-without-logging)))

(defn initialize-with-registry
  []
  (component/map->SystemMap
    (merge (cfg)
           log
           reg)))

(def init-lookup
  {:basic #'initialize-bare-bones
   :config-only #'initialize-config-only
   :nolog #'initialize-without-logging
   :main #'initialize-with-registry})

(defn init
  ([]
    (init :main))
  ([mode]
    ((mode init-lookup))))

(def cli #(init :nolog))
(def integration-testing #(init :config-only))
(def testing #(init :nolog))
