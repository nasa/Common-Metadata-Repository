(ns cmr.common.cache
  "A system level cache based on clojure.core.cache library.
  Follows basic usage pattern as given in - https://github.com/clojure/core.cache/wiki/Using"
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [clojure.core.cache :as cc]))

(def general-cache-key
  "The key used to store the general cache in the system cache map."
  :general)

(defn cache-lookup
  "Looks up the value of the cached item using the key. If there is a cache miss it will invoke
  the function given with no arguments, save the value in the cache and return the value."
  [cmr-cache key f]
  (-> (swap! (:atom cmr-cache)
             (fn [cache]
               (if (cc/has? cache key)
                 (cc/hit cache key)
                 (cc/miss cache key (f)))))
      (get key)))

(defmulti create-core-cache
  "Create a cache using cmr.core-cache of the given type."
  (fn [type value opts]
    type))

(defmethod create-core-cache :default
  [type value opts]
  (cc/basic-cache-factory value))

(defmethod create-core-cache :lru
  [type value opts]
  (cc/lru-cache-factory value opts))

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

(defn update-cache
  "Update the cache contents with the output of the given funciton, f. f takes the
  current cache as its input and returns the new cache state."
  [cmr-cache f]
  (let [core-cache (deref (:atom cmr-cache))
        new-state (f cmr-cache)
        ;; FIXME - this relies on an implementation details of cmr.core.cache, which is BAD
        new-cache (merge core-cache new-state)]
    (swap! (:atom cmr-cache) (fn [_] new-cache))))

(defn set-cache-key-value
  "Set a single key/value pair for the cache."
  [cmr-cache cache-key value]
  (let [core-cache (deref (:atom cmr-cache))]
    (swap! (:atom cmr-cache)
           (fn [_] (cc/miss core-cache cache-key value)))))




