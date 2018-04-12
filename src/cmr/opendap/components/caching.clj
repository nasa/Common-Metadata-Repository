(ns cmr.opendap.components.caching
  (:require
    [clojure.core.cache :as cache]
    [clojure.java.io :as io]
    [com.stuartsierra.component :as component]
    [cmr.opendap.components.config :as config]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/utility Data & Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-cache
  [system]
  (if-let [sys system]
    (if-let [filename (config/cache-dumpfile system)]
      (try
        (read-string
          (slurp filename))
        (catch Exception _ nil)))))

(defn dump-cache
  [system cache-data]
  (let [dumpfile (config/cache-dumpfile system)]
    (io/make-parents dumpfile)
    (spit
      dumpfile
      (prn-str cache-data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Caching Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-cache
  [system]
  (let [init (merge (config/cache-init system)
                    (load-cache system))
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
  ([system item-key]
    (cache/lookup @(get-cache system) item-key))
  ([system item-key value-fn]
    (let [ch @(get-cache system)]
      (if (cache/has? ch item-key)
        (do
          (log/debugf "Cache has key %s; skipping value function ..."
                      item-key)
          (cache/hit ch item-key))
        (when-let [value (value-fn)]
          (log/debug "Cache miss; calling value function ...")
          (swap! (get-cache system) #(cache/miss % item-key value)))))
    (lookup system item-key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Caching [cache])

(defn start
  [this]
  (log/info "Starting caching component ...")
  (let [cache (create-cache this)]
    (log/debug "Started caching component.")
    (assoc this :cache cache)))

(defn stop
  [this]
  (log/info "Stopping caching component ...")
  (if-let [cache-ref (:cache this)]
    (if-let [cache @cache-ref]
      (dump-cache this cache)))
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
