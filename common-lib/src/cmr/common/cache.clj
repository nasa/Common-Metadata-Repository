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


;; TODO add a cubby implemented version of this protocol
;; TODO add a hash version of this protocol
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


;; TODO move this to it's own namespace

;; Implements the CmrCache protocol using an in memory cache store. The cache data is saved in memory
;; in a clojure.core.cache type in an atom
(defrecord InMemoryCache
  [
   ;; Contains the initial version of the cache.
   initial-cache

   ;; Atom containing an in memory cache
   cache-atom]

  CmrCache
  (get-keys
    [this]
    (keys @cache-atom))

  (get-value
    [this key]
    ;; TODO this should result in a hit or a miss like the other one.
    (get @cache-atom key))

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

(defn reset-caches
  "Clear all caches."
  [context]
  (doseq [[k v] (get-in context [:system :caches])]
    (reset v)))


(comment

  ;; TODO test the consistent cache by adding a new integration test to cubby
  ;; look first to see if there's a way to unit test this in common app lib without talking directly to cubby.

  ;; TODO add another cache type possibly in another namespace. The cache type will be called :consistent-cache
  ;; I want to create a new cache type that will store items in memory. Whenever an item is retrieved
  ;; from the cache we'll fetch it out of the cache and then get it's hash code. (the hash code may be
  ;; stored as well. A cached item might be {:hash xxx :item <cached thing>}) We'll then do a lookup
  ;; in cubby for the hash code of that cached item. The key used in cubby for the cached thing will
  ;; be "<key>-hash-code"

  ;; What do we do if cubby is down?
  ;; Let the indexer fail
  ;; TODO add cubby to the indexer health check


  ;; TODO do we need to consider on writing the cache value to cubby that we should refresh the elastic index?
  ;; We might need to do this because we want things after the write to cubby to all see the same value.
  ;; This could be an option we pass in to cubby. I think there's an option you can pass in elastisch to force this on write.



  )