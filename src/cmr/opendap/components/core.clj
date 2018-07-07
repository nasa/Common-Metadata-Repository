(ns cmr.opendap.components.core
  (:require
    [cmr.authz.components.caching :as auth-caching]
    [cmr.mission-control.components.pubsub :as pubsub]
    [cmr.opendap.components.auth :as auth]
    [cmr.opendap.components.caching :as concept-caching]
    [cmr.opendap.components.concept :as concept]
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

(def httpd
  {:httpd (component/using
           (httpd/create-component)
           [:config :logging :pubsub
            :auth-caching :auth
            :concept-caching :concepts])})

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

(def httpd-without-logging
  {:httpd (component/using
           (httpd/create-component)
           [:config :pubsub :auth-caching :auth :concept-caching :concepts])})

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
           pubsub
           auth-cache
           authz
           concept-cache
           concepts
           httpd)))

(defn initialize-without-logging
  []
  (component/map->SystemMap
    (merge cfg
           pubsub-without-logging
           auth-cache-without-logging
           authz
           concept-cache-without-logging
           concepts
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
