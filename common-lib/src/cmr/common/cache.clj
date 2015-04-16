(ns cmr.common.cache
  "Defines the core caching protocol for the CMR.")

(def general-cache-key
  "The key used to store the general cache in the system cache map."
  :general)

(defn context->cache
  "Get the cache for the given key from the context"
  [context cache-key]
  (get-in context [:system :caches cache-key]))

;; TODO add a cubby implemented version of this protocol
(defprotocol CmrCache
  "Defines a protocol used for caching data."
  (get-keys
    [cache]
    "Returns the list of keys for the given cache. The keys are conveted to non-keyword strings.")

  (get-value
    [cache key]
    [cache key lookup-fn]
    "Looks up the value of the cached item using the key. If there is a cache miss
    and a function is provided it will invoke the function given with no arguments, save the
    returned value in the cache, then return the value. If no function is provided it will return nil
    for cache misses.")

  (reset
    [cache]
    "Resets the cache back to its initial state.")

  (set-value
    [cache key value]
    "Associates the value in the cache with the given key."))

(defn reset-caches
  "Clear all caches found in the system."
  [context]
  (doseq [[k v] (get-in context [:system :caches])]
    (reset v)))


