(ns cmr.authz.components.caching
  (:require
   [clojure.core.cache :as cache]
   [clojure.java.io :as io]
   [cmr.authz.components.config :as config]
   [com.stuartsierra.component :as component]
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
  ([system]
   (create-cache system
                 (merge (config/cache-init system)
                        (load-cache system))))
  ([system init-items]
   (let [ttl (config/cache-ttl-ms system)
         threshold (config/cache-lru-threshold system)
         cache (-> init-items
                   (cache/ttl-cache-factory :ttl ttl)
                   (cache/lru-cache-factory :threshold threshold))]
     (log/debug "Creating TTL Cache with time-to-live of" ttl)
     (log/debug "Composing with LRU cache with threshold (item count)" threshold)
     (log/trace "Starting value:" init-items)
     cache)))

(defn get-cache
  [system]
  (get-in system [:auth-caching :cache]))

(defn evict
  [system item-key]
  (swap! (get-cache system) cache/evict item-key))

(defn evict-all
  [system]
  (reset! (get-cache system) (create-cache system (config/cache-init system))))

(defn lookup
  ([system item-key]
    (cache/lookup @(get-cache system) item-key))
  ([system item-key value-fn]
    (let [ch @(get-cache system)]
      (if (cache/has? ch item-key)
        (do
          (log/debug "Cache has key; skipping value function ...")
          (log/trace "Key:" item-key)
          (cache/hit ch item-key))
        (when-let [value (value-fn)]
          (log/debug "Cache miss; calling value function ...")
          (log/trace "Key:" item-key)
          (log/trace "Value missed:" value)
          (when-not (or (nil? value) (empty? value))
            (swap! (get-cache system) #(cache/miss % item-key value))))))
    (lookup system item-key)))

(defn lookup-all
  [system]
  @(get-cache system))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord AuthzCaching [cache])

(defn start
  [this]
  (log/info "Starting authz caching component ...")
  (let [cache (atom (create-cache this))]
    (log/debug "Started authz caching component.")
    (assoc this :cache cache)))

(defn stop
  [this]
  (log/info "Stopping authz caching component ...")
  (if-let [cache-ref (:cache this)]
    (if-let [cache @cache-ref]
      (dump-cache this cache)))
  (log/debug "Stopped authz caching component.")
  (assoc this :cache nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend AuthzCaching
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->AuthzCaching {}))
