(ns cmr.cubby.data.memory-cache-store
  "Defines an in memory version of the persistent cache store."
  (:require [cmr.cubby.data :as d]
            [cmr.common.lifecycle :as l]))

(def initial-cache-state {})

(defrecord MemoryCacheStore
  [data-atom]

  l/Lifecycle

  (start
    [this system]
    this)

  (stop
    [this system]
    this)

  d/PersistentCacheStore

  (get-keys
    [this]
    (keys @data-atom))

  (get-value
    [this key-name]
    (get @data-atom key-name))

  (set-value
    [this key-name value]
    (swap! data-atom assoc key-name value))

  (delete-value
    [this key-name]
    (swap! data-atom dissoc key-name))

  (reset
    [this]
    (reset! data-atom initial-cache-state)))

(defn create-memory-cache-store
  []
  (->MemoryCacheStore (atom initial-cache-state)))