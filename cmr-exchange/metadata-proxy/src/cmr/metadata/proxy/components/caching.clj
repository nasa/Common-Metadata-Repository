(ns cmr.metadata.proxy.components.caching
  (:require
   [clojure.core.cache :as cache]
   [clojure.java.io :as io]
   [cmr.metadata.proxy.components.config :as config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/utility Data & Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-cache
  [system]
  (if-let [sys system]
    (if-let [filename (config/concept-cache-dumpfile system)]
      (try
        (read-string
          (slurp filename))
        (catch Exception _ nil)))))

(defn dump-cache
  [system cache-data]
  (let [dumpfile (config/concept-cache-dumpfile system)]
    (io/make-parents dumpfile)
    (spit
      dumpfile
      (prn-str cache-data))))

(defn item-has-value?
  [item]
  (cond (nil? item) false
        (and (seq? item) (empty? item)) false
        :else true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Caching Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-cache
  ([system]
   (create-cache system
                 (merge (config/concept-cache-init system)
                        (load-cache system))))
  ([system init-items]
   (let [cache (-> init-items
                   cache/soft-cache-factory ; will GC with memory pressyre
                   cache/lru-cache-factory)]
     (log/debug "Creating memory-sensitive cache composed with LRU cache ...")
     (log/trace "Starting value:" init-items)
     cache)))

(defn get-cache
  [system]
  (get-in system [:concept-caching :cache]))

(defn evict
  [system item-key]
  (swap! (get-cache system) cache/evict item-key))

(defn evict-all
  [system]
  (reset! (get-cache system)
          (create-cache system (config/concept-cache-init system))))

(defn has?
  [system item-key]
  (cache/has? @(get-cache system) item-key))

(defn- -has-all?
  [ch item-keys]
  (every? #(cache/has? ch %) item-keys))

(defn has-all?
  [system item-keys]
  (let [ch @(get-cache system)]
    (-has-all? ch item-keys)))

(defn lookup
  ([system item-key]
    (cache/lookup @(get-cache system) item-key))
  ([system item-key value-fn]
    (let [ch @(get-cache system)]
      (if (cache/has? ch item-key)
        (do
          (log/debug "Concept cache has key; skipping value function ...")
          (log/trace "Key:" item-key)
          (cache/hit ch item-key))
        (when-let [value (value-fn)]
          (log/debug "Concept cache miss; calling value function ...")
          (when (item-has-value? value)
            (swap! (get-cache system) #(cache/miss % item-key value))))))
    (lookup system item-key)))

(defn lookup-many
  ([system item-keys]
    (let [ch @(get-cache system)]
      (map #(cache/lookup ch %) item-keys)))
  ([system item-keys value-fn]
    (let [ch @(get-cache system)]
      (if (-has-all? ch item-keys)
        (do
          (log/debug "Concept cache has all keys; skipping value function ...")
          (log/trace "Keys:" item-keys)
          (mapv #(cache/hit ch %) item-keys))
        (when-let [key-values-map (value-fn)]
          (log/debug (str "Concept cache miss for at least one key; "
                          "calling value function ..."))
          (dorun
            (for [[k v] key-values-map]
              (when (item-has-value? v)
                (swap! (get-cache system) #(cache/miss % k v))))))))
    (lookup-many system item-keys)))

(defn lookup-all
  [system]
  @(get-cache system))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ConceptCaching [cache])

(defn start
  [this]
  (log/info "Starting concept caching component ...")
  (let [cache (atom (create-cache this))]
    (log/debug "Started concept caching component.")
    (assoc this :cache cache)))

(defn stop
  [this]
  (log/info "Stopping concept caching component ...")
  (if-let [cache-ref (:cache this)]
    (if-let [cache @cache-ref]
      (dump-cache this cache)))
  (log/debug "Stopped concept caching component.")
  (assoc this :cache nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend ConceptCaching
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->ConceptCaching {}))
