(ns cmr.common.cache
  "A system level cache based on clojure.core.cache library.
  Follows basic usage pattern as given in - https://github.com/clojure/core.cache/wiki/Using"
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [clojure.core.cache :as cc]))

(def general-cache-key
  "The key used to store the general cache in the system cache map."
  :general)

(defn context->cache
  "Get the cache for the given key from the context"
  [context cache-key]
  (get-in context [:system :caches cache-key]))

(defn cache-keys
  "Return the list of keys for the given cache. The keys are conveted to non-keyword strings."
  [cmr-cache]
  (->> cmr-cache
       :atom
       deref
       keys))

(defn cache-lookup
  "Looks up the value of the cached item using the key. If there is a cache miss
  and a function is provided it will invoke the function given with no arguments, save the
  value in the cache, and return the value. If no function is provided it will return nil
  for cache misses."
  ([cmr-cache key]
   (-> cmr-cache
       :atom
       deref
       (get key)))
  ([cmr-cache key f]
   (-> (swap! (:atom cmr-cache)
              (fn [cache]
                (if (cc/has? cache key)
                  (cc/hit cache key)
                  (cc/miss cache key (f)))))
       (get key))))

(defmulti create-core-cache
  "Create a cache using cmr.core-cache of the given type."
  (fn [type value opts]
    type))

(defmethod create-core-cache :default
  [type value opts]
  (cc/basic-cache-factory value))

(defmethod create-core-cache :lru
  [type value opts]
  (apply cc/lru-cache-factory value (flatten (seq opts))))

(defmethod create-core-cache :ttl
  [type value opts]
  (apply cc/ttl-cache-factory value (flatten (seq opts))))

(defn create-cache
  "Create system level cache. The currently supported cache types are :defalut and :lru.
  The :default type does not do cache evictions - cache items must be explicitly removed.
  The :lru (Least Recently Used) cache evicts items that have not been used recently when
  the cache size exceeds the threshold (default 32). This threshold can be set using the
  :threshold key in the opts parameter."
  ([]
   (create-cache :default {} {}))
  ([cache-type]
   (create-cache cache-type {} {}))
  ([cache-type initial-cache-value]
   (create-cache cache-type initial-cache-value {}))
  ([cache-type initial-cache-value opts]
   (let [initial-cache (create-core-cache cache-type initial-cache-value opts)]
     {:initial initial-cache
      :atom (atom initial-cache)})))

(defn reset-cache
  [cmr-cache]
  (reset! (:atom cmr-cache) (:initial cmr-cache)))

(defn reset-caches
  "Clear all caches."
  [context]
  (doseq [[k v] (get-in context [:system :caches])]
    (reset-cache v)))

(defn update-cache
  "Update the cache contents with the output of the given function, f. f takes the
  current cache as its input and returns the new cache."
  [cmr-cache f]
  (swap! (:atom cmr-cache) f)
  cmr-cache)