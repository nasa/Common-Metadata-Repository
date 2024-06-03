(ns cmr.common.cache
  "Defines the core caching protocol for the CMR."
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer (info)]))

(defn context->cache
  "Get the cache for the given key from the context"
  [context cache-key]
  (or (get-in context [:system :caches cache-key])
      ;; The job scheduler uses a subset of the system context.
      (get-in context [:caches cache-key])))

(defprotocol CmrCache
  "Defines a protocol used for caching data."
  (get-keys
    [cache]
    "Returns the list of keys for the given cache. The keys are converted to non-keyword strings.")

  (key-exists
    [cache cache-key]
    "Returns true if the key exists in the cache. This is used to determine if the cache has been
     set up.")

  (get-value
    [cache cache-key]
    [cache cache-key lookup-fn]
    "Looks up the value of the cached item using the key. If there is a cache miss
     and a function is provided it will invoke the function given with no arguments, save the
     returned value in the cache, then return the value. If no function is provided it will return
     nil for cache misses.")

  (reset
    [cache]
    "Resets the cache back to its initial state.")

  (set-value
    [cache cache-key value]
    "Associates the value in the cache with the given key.")

  (cache-size
   [cache]
   "Returns the size of the cache in bytes."))

(defn simple-cache?
  "Function that takes a cache and checks to see if the cache uses the CmrCache protocol,
   simple cmr.common.cache. Currently there are two protocol types cmr.common.cache and
   cmr.common.hash-cache. This function does the check using string comparison. Using instance?
   forces non common libraries to be included in common and produces circular dependencies."
  [cache]
  (not (string/includes? (str (type cache)) "hash_cache")))

(defn reset-caches
  "Clear all caches found in the system, this includes the caches of embedded systems."
  [context]
  (doseq [[_ v] (get-in context [:system :caches])]
    (when (simple-cache? v)
      (reset v)))
  ;; reset embedded systems caches
  (doseq [[_ v] (get-in context [:system :embedded-systems])]
    (when (simple-cache? v)
      (reset-caches {:system v}))))

(defn cache-sizes
  "Returns a map of caches and their sizes in bytes."
  [context]
  (let [system-caches (get-in context [:system :caches])]
    (into {}
          (for [[cache-key cache] system-caches]
            (when (simple-cache? cache)
              (try
                {cache-key (cache-size cache)}
                (catch java.lang.Exception e
                  (info (format "Either the Cache %s or its delegate is null. The exception is %s"
                                cache-key
                                e))
                  {cache-key 0})))))))
