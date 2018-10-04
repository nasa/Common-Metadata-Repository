(ns cmr.opendap.components.core
  (:require
    [cmr.authz.components.caching :as auth-caching]
    [cmr.exchange.common.components.config :as base-config]
    [cmr.exchange.common.components.logging :as logging]
    [cmr.http.kit.components.server :as httpd]
    [cmr.metadata.proxy.components.auth :as auth]
    [cmr.metadata.proxy.components.caching :as concept-caching]
    [cmr.metadata.proxy.components.concept :as concept]
    [cmr.mission-control.components.pubsub :as pubsub]
    [cmr.opendap.config :as config-lib]
    [cmr.ous.components.config :as config]
    [cmr.plugin.jar.components.registry :as plugin-registry]
    [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Common Configuration Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cfg
  []
  {:config (base-config/create-component (config-lib/data))})

(def log
  {:logging (component/using
             (logging/create-component)
             [:config])})

(def reg
  {:plugin (component/using
            (plugin-registry/create-component)
            [:config :logging])})

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

(defn httpd
  [cfg-data]
  {:httpd (component/using
           (httpd/create-component (config/http-port cfg-data))
           [:config :logging :plugin
            :pubsub :auth-caching :auth
            :concept-caching :concepts])})

;;; Additional components for systems that want to supress logging (e.g.,
;;; systems created for testing).

(def reg-without-logging
  {:plugin (component/using
            (plugin-registry/create-component)
            [:config])})

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

(defn httpd-without-logging
  [cfg-data]
  {:httpd (component/using
           (httpd/create-component (config/http-port cfg-data))
           [:config :plugin :pubsub
            :auth-caching :auth
            :concept-caching :concepts])})

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
  (let [cfg-data (initialize-bare-bones)]
    (component/map->SystemMap
      (merge cfg-data
             reg
             pubsub
             auth-cache
             authz
             concept-cache
             concepts
             (httpd cfg-data)))))

(defn initialize-without-logging
  []
  (let [cfg-data (cfg)]
    (component/map->SystemMap
      (merge cfg-data
             reg-without-logging
             pubsub-without-logging
             auth-cache-without-logging
             authz
             concept-cache-without-logging
             concepts
             (httpd-without-logging cfg-data)))))

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
