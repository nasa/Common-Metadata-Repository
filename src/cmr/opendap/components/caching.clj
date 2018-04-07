(ns cmr.opendap.components.caching
  (:require
    [clojure.core.cache :as cache]
    [com.stuartsierra.component :as component]
    [cmr.opendap.components.config :as config]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/utility Data & Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TBD

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Caching Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-cache
  [system]
  (let [init (config/cache-init system)
        ttl (config/cache-ttl-ms system)
        threshold (config/cache-lru-threshold system)
        cache (-> init
                  (cache/ttl-cache-factory :ttl ttl)
                  (cache/lru-cache-factory :threshold threshold))]
    (log/debug "Creating TTL Cache with time-to-live of" ttl)
    (log/debug "Composing with LRU cache with threshold (item count)" threshold)
    (log/trace "Starting value:" init)
    (atom cache)))

(defn get-cache
  [system]
  (get-in system [:caching :cache]))

(defn evict
  [system item-key]
  (swap! (get-cache system) cache/evict item-key))

(defn lookup
  [system item-key]
  (cache/lookup @(get-cache system) item-key))

(defn through-cache!
  [system item-key val-fn]
  (swap! (get-cache system) cache/through-cache item-key val-fn)
  (lookup system item-key))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Caching [])

(defn start
  [this]
  (log/info "Starting caching component ...")
  (let [cache (create-cache this)]
    (log/debug "Started caching component.")
    (assoc this :cache cache)))

(defn stop
  [this]
  (log/info "Stopping caching component ...")
  (log/debug "Stopped caching component.")
  (assoc this :cache nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Caching
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Caching {}))
