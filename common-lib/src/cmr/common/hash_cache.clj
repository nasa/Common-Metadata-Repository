(ns cmr.common.hash-cache
  "Defines the core hash caching protocol for the CMR."
  (:require
   [clojure.string :as string]))

(defn context->cache
  "Get the cache for the given key from the context"
  [context cache-key]
  (or (get-in context [:system :caches cache-key])
      ;; The job scheduler uses a subset of the system context.
      (get-in context [:caches cache-key])))

(defn hash-cache?
  "Function that takes a cache and checks to see if the
   cache is a hash-cache."
  [cache]
  (string/includes? (str (type cache)) "hash_cache"))

(defprotocol CmrHashCache
  "Defines a protocol used for caching data using hashes."
  (get-map
    [cache cache-key]
    "Returns the entire hash map from the cache for the given cache named by the passed in key.")

  (key-exists
    [cache cache-key]
    "Returns true if the key exists in the cache. This is used to determine if the cache has been
     set up.")

  (get-keys
    [cache cache-key]
    "Returns the list of fields (keys) in a hashmap for the given cache named by the passed in key.
    The keys are converted to non-keyword strings.")

  (get-value
    [cache cache-key field]
    "Looks up the value of the cached item using the key to get the cache and the field (hash map
     key) to get to the value.")

  (get-values
    [cache cache-key fields]
    "Looks up the values corresponding to the past in fields list using the key to get to the
     correct hash map.")

  (reset
    [cache]
    [cache cache-key]
    "Resets the cache back to its initial state. The first function relies on the hash map key was
     passed in when the hash was created. the latter function just clears the hash map named by the
     key.")

  (set-value
    [cache cache-key field value]
    "Associates the value in the hash map cache (named by key) with the given field.")

  (set-values
    [cache cache-key field-value-map]
     "Inserts a set of fields and values into a hashmap.")

  (remove-value
   [cache cache-key field]
   "Removes the field from the hash cache.")

  (cache-size
   [cache cache-key]
   "Returns the size of the cache in bytes."))

(defn reset-caches
  "Clear all caches found in the system, this includes the caches of embedded systems."
  [context]
  (doseq [[_ v] (get-in context [:system :caches])]
    (when (hash-cache? v)
      (reset v)))
  ;; reset embedded systems caches
  (doseq [[_ v] (get-in context [:system :embedded-systems])]
    (when (hash-cache? v)
      (reset-caches {:system v}))))

(defn cache-sizes
  "Returns a map of caches and their sizes in bytes."
  [context]
  (let [system-caches (get-in context [:system :caches])]
    (into {}
          (for [[cache-key cache] system-caches]
            (when (hash-cache? cache)
              {cache-key (cache-size cache cache-key)})))))
