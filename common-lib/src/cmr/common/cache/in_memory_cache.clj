(ns cmr.common.cache.in-memory-cache
  "A system level cache based on clojure.core.cache library.
  Follows basic usage pattern as given in - https://github.com/clojure/core.cache/wiki/Using"
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [clojure.core.cache :as cc]
            [cmr.common.cache :as c]))

;; Implements the CmrCache protocol using an in memory cache store. The cache data is saved in memory
;; in a clojure.core.cache type in an atom
(defrecord InMemoryCache
  [
   ;; Contains the initial version of the cache.
   initial-cache

   ;; Atom containing an in memory cache
   cache-atom]

  c/CmrCache
  (get-keys
    [this]
    (keys @cache-atom))

  (get-value
    [this key]
    (-> (swap! cache-atom
               (fn [cache]
                 (if (cc/has? cache key)
                   (cc/hit cache key)
                   ;; We don't do a miss here because the miss expects a value to be stored along
                   ;; with it. We can't use nil because it would end up storing nil with the key
                   ;; and potentially pushing out other valid values.
                   cache)))
        (get key)))

  (get-value
    [this key lookup-fn]
    (-> (swap! cache-atom
               (fn [cache]
                 (if (cc/has? cache key)
                   (cc/hit cache key)
                   (cc/miss cache key (lookup-fn)))))
        (get key)))

  (reset
    [this]
    (reset! cache-atom initial-cache))

  (set-value
    [this key value]
    (swap! cache-atom assoc key value)))

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


(defn create-in-memory-cache
  "Create in memory cache with different cache types for the internal cache. The currently supported
  cache types are :default and :lru.
  The :default type does not do cache evictions - cache items must be explicitly removed.
  The :lru (Least Recently Used) cache evicts items that have not been used recently when
  the cache size exceeds the threshold (default 32). This threshold can be set using the
  :threshold key in the opts parameter."
  ([]
   (create-in-memory-cache :default {} {}))
  ([cache-type]
   (create-in-memory-cache cache-type {} {}))
  ([cache-type initial-cache-value]
   (create-in-memory-cache cache-type initial-cache-value {}))
  ([cache-type initial-cache-value opts]
   (let [initial-cache (create-core-cache cache-type initial-cache-value opts)]
     (map->InMemoryCache
       {:initial-cache initial-cache
        :cache-atom (atom initial-cache)}))))