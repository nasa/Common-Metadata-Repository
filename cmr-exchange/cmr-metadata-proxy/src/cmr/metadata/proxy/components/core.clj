(ns cmr.metadata.proxy.components.core
  (:require
    [cmr.authz.components.caching :as auth-caching]
    [cmr.exchange.common.components.config :as config]
    [cmr.exchange.common.components.logging :as logging]
    [cmr.metadata.proxy.components.auth :as auth]
    [cmr.metadata.proxy.components.caching :as concept-caching]
    [cmr.metadata.proxy.components.concept :as concept]
    [cmr.metadata.proxy.config :as config-lib]
    [cmr.mission-control.components.pubsub :as pubsub]
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

(def auth-cache
  {:auth-caching (component/using
                  (auth-caching/create-component)
                  [:config :logging])})

(def authz
  {:auth (component/using
          (auth/create-component)
          [:auth-caching :pubsub])})

(def concept-cache
  {:concept-caching (component/using
                     (concept-caching/create-component)
                     [:config :logging])})

(def concepts
  {:concepts (component/using
              (concept/create-component)
              [:concept-caching :pubsub])})

;;; Additional components for systems that want to supress logging (e.g.,
;;; systems created for testing).

(def pubsub-without-logging
  {:pubsub (component/using
            (pubsub/create-component)
            [:config])})

(def auth-cache-without-logging
  {:auth-caching (component/using
                  (auth-caching/create-component)
                  [:config])})

(def concept-cache-without-logging
  {:concept-caching (component/using
                     (concept-caching/create-component)
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

(defn initialize
  []
  (component/map->SystemMap
    (merge (initialize-bare-bones)
           pubsub
           auth-cache
           authz
           concept-cache
           concepts)))

(defn initialize-without-logging
  []
  (component/map->SystemMap
    (merge (cfg)
           pubsub-without-logging
           auth-cache-without-logging
           authz
           concept-cache-without-logging
           concepts)))

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
