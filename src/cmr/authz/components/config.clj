(ns cmr.authz.components.config
  (:require
   [cmr.authz.config :as config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-cfg
  [system]
  (->> [:config :data]
       (get-in system)
       (into {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-service
  [system service]
  (let [svc-cfg (get-in (get-cfg system)
                        (concat [:cmr] (config/service-keys service)))]
    svc-cfg))

(defn get-service-url
  [system service]
  (config/service->url (get-service system service)))

;; The URLs returned by these functions have no trailing slash:
(def get-access-control-url #(get-service-url % :access-control))
(def get-echo-rest-url #(get-service-url % :echo-rest))

(defn cache-dumpfile
  [system]
  (get-in (get-cfg system) [:auth-caching :dumpfile]))

(defn cache-init
  [system]
  (get-in (get-cfg system) [:auth-caching :init]))

(defn cache-lru-threshold
  [system]
  (get-in (get-cfg system) [:auth-caching :lru :threshold]))

(defn cache-ttl-ms
  [system]
  (* (get-in (get-cfg system) [:auth-caching :ttl :minutes]) ; minutes
     60 ; seconds
     1000 ; milliseconds
     ))

(defn cache-type
  [system]
  (get-in (get-cfg system) [:auth-caching :type]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Config [data])

(defn start
  [this]
  (log/info "Starting config component ...")
  (log/debug "Started config component.")
  (let [cfg (config/cfg-data)]
    (log/trace "Built configuration:" cfg)
    (assoc this :data cfg)))

(defn stop
  [this]
  (log/info "Stopping config component ...")
  (log/debug "Stopped config component.")
  this)

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Config
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Config {}))
