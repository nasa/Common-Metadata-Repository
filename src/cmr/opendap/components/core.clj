(ns cmr.opendap.components.core
  (:require
    [cmr.authz.components.caching :as auth-caching]
    [cmr.opendap.components.auth :as auth]
    [cmr.opendap.components.caching :as concept-caching]
    [cmr.opendap.components.config :as config]
    [cmr.opendap.components.httpd :as httpd]
    [cmr.opendap.components.logging :as logging]
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

(def auth-cache
  {:auth-caching (component/using
                  (auth-caching/create-component)
                  [:config :logging])})

(def authz
  {:auth (component/using
          (auth/create-component)
          [:auth-caching])})

(def concept-cache
  {:concept-caching (component/using
                     (concept-caching/create-component)
                     [:config :logging])})

(def httpd
  {:httpd (component/using
           (httpd/create-component)
           [:config :logging :auth-caching :auth])})

(def auth-cache-without-logging
  {:auth-caching (component/using
                  (auth-caching/create-component)
                  [:config])})

(def concept-cache-without-logging
  {:concept-caching (component/using
                     (concept-caching/create-component)
                     [:config])})

(def httpd-without-logging
  {:httpd (component/using
           (httpd/create-component)
           [:config :auth-caching :auth :concept-caching])})

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
           auth-cache
           authz
           concept-cache
           httpd)))

(defn initialize-without-logging
  []
  (component/map->SystemMap
    (merge cfg
           auth-cache-without-logging
           authz
           concept-cache-without-logging
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
