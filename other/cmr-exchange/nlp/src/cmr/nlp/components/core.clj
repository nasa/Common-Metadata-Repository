(ns cmr.nlp.components.core
  (:require
   [cmr.exchange.common.components.config :as config]
   [cmr.exchange.common.components.logging :as logging]
   [cmr.mission-control.components.pubsub :as pubsub]
   [cmr.nlp.components.elastic :as elastic]
   [cmr.nlp.config :as config-lib]
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

(def pubsub
  {:pubsub (component/using
            (pubsub/create-component)
            [:config :logging])})

(def es
  {:elastic (component/using
             (elastic/create-component)
             [:config :logging :pubsub])})

;;; Additional components for systems that want to supress logging (e.g.,
;;; systems created for testing).

(def pubsub-without-logging
  {:pubsub (component/using
            (pubsub/create-component)
            [:config])})

(def es-without-logging
  {:elastic (component/using
             (elastic/create-component)
             [:config :pubsub])})

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

(defn initialize
  []
  (component/map->SystemMap
    (merge (initialize-bare-bones)
           pubsub
           es)))

(defn initialize-without-logging
  []
  (component/map->SystemMap
    (merge (cfg)
           pubsub-without-logging
           es-without-logging)))

(def init-lookup
  {:basic #'initialize-bare-bones
   :testing-config-only #'initialize-config-only
   :testing #'initialize-without-logging
   :main #'initialize})

(defn init
  ([]
    (init :main))
  ([mode]
    ((mode init-lookup))))

(def testing #(init :testing))
(def testing-config-only #(init :testing-config-only))
