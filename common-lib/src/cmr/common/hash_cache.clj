(ns cmr.common.hash-cache
  "Defines the core hash caching protocol for the CMR."
  (:require
   [cmr.common.cache :as cache]))

(defn context->cache
  "Get the cache for the given key from the context"
  [context cache-key]
  (cache/context->cache context cache-key))

(defprotocol CmrHashCache
  "Defines a protocol used for caching data using hashes."
  (get-map
   [cache key])
  
  (get-keys
    [cache key]
    "Returns the list of fields (keys) in a hashmap for the given cache named by the passed in key.
    The keys are converted to non-keyword strings.")

  (get-value
    [cache key field]
    "Looks up the value of the cached item using the key to get the cache and the field (hash map key)
    to get to the value.")
  
  (get-values
    [cache key fields]
    "Looks up the values corresponding to the past in fields list using the key to get to the correct
    hash map.")

  (reset
    [cache]
    [cache key]
    "Resets the cache back to its initial state. The first function relies on the hash map key was passed
    in when the hash was created. the latter funtion just clears the hash map named by the key.")

  (set-value
    [cache key field value]
    "Associates the value in the hash map cache (named by key) with the given field.")
  
  (set-values
    [cache key field-value-map]
     "Inserts a set of fields and values into a hashmap.")
  
  (cache-size
   [cache key]
   "Returns the size of the cache in bytes."))

(defn reset-caches
  "Clear all caches found in the system, this includes the caches of embedded systems."
  [context]
  (cache/reset-caches context))

(defn cache-sizes
  "Returns a map of caches and their sizes in bytes."
  [context]
  (cache/cache-sizes context))
