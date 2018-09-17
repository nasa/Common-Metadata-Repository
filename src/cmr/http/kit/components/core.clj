(ns cmr.http.kit.components.core
  (:require
    [cmr.http.kit.components.config :as config]
    [cmr.http.kit.components.logging :as logging]
    [cmr.http.kit.components.server :as httpd]
    [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Common Configuration Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cfg
  {:config (config/create-component)})

(def log
  {:logging (component/using
             (logging/create-component)
             [:config])})

(def httpd
  {:httpd (component/using
           (httpd/create-component)
           [:config :logging])})

;;; Additional components for systems that want to supress logging (e.g.,
;;; systems created for testing).

(def httpd-without-logging
  {:httpd (component/using
           (httpd/create-component)
           [:config])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initializations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-config-only
  []
  (component/map->SystemMap cfg))

(defn initialize-bare-bones
  []
  (component/map->SystemMap
    (merge cfg
           log)))

(defn initialize-with-web
  []
  (component/map->SystemMap
    (merge cfg
           log
           httpd)))

(defn initialize-without-logging
  []
  (component/map->SystemMap
    (merge cfg
           httpd-without-logging)))

(def init-lookup
  {:basic #'initialize-bare-bones
   :testing-config-only #'initialize-config-only
   :testing #'initialize-without-logging
   :web #'initialize-with-web})

(defn init
  ([]
    (init :web))
  ([mode]
    ((mode init-lookup))))
