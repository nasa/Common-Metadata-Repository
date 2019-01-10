(ns cmr.http.kit.components.core
  (:require
    [cmr.exchange.common.components.config :as config]
    [cmr.exchange.common.components.logging :as logging]
    [cmr.http.kit.components.server :as httpd]
    [cmr.http.kit.config :as config-lib]
    [cmr.plugin.jar.components.registry :as plugin-registry]
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
            (plugin-registry/create-component)
            [:config :logging])})

(def httpd
  {:httpd (component/using
           (httpd/create-component)
           [:config :logging :plugin])})

;;; Additional components for systems that want to supress logging (e.g.,
;;; systems created for testing).

(def reg-without-logging
  {:plugin (component/using
            (plugin-registry/create-component)
            [:config])})

(def httpd-without-logging
  {:httpd (component/using
           (httpd/create-component)
           [:config :plugin])})

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

(defn initialize-with-web
  []
  (component/map->SystemMap
    (merge (cfg)
           log
           reg
           httpd)))

(defn initialize-without-logging
  []
  (component/map->SystemMap
    (merge (cfg)
           reg-without-logging
           httpd-without-logging)))

(def init-lookup
  {:basic #'initialize-bare-bones
   :testing-config-only #'initialize-config-only
   :testing #'initialize-without-logging
   :main #'initialize-with-web})

(defn init
  ([]
    (init :main))
  ([mode]
    ((mode init-lookup))))
