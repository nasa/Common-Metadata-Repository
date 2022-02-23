(ns cmr.common.cache.in-memory-cache
  "A system level cache based on clojure.core.cache library.

  Follows basic usage pattern as given in:
    https://github.com/clojure/core.cache/wiki/Using"
  (:require
   [clojure.core.cache :as cc :refer [defcache]]
   [cmr.common.cache :as c]
   [cmr.common.log :refer [debug error]]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer])
  (:import
   (clojure.core.cache CacheProtocol)))

(defmulti size-in-bytes class)

(defmethod size-in-bytes :default
  [x]
  (try
    (debug "No handler found size-in-bytes for" (class x) " Using default on " x)
    (size-in-bytes (str x))
    (catch Exception e
      (error (str "A problem occurred calculating cache size. "
                 "Cache usage estimates may be incorrect. "
                 (.getMessage e)))
      1)))

(defmethod size-in-bytes clojure.lang.Keyword
  [kw]
  (count (.getBytes (name kw) "UTF-8")))

(defmethod size-in-bytes java.lang.String
  [x]
  (count (.getBytes x "UTF-8")))

(defmethod size-in-bytes java.lang.Integer
  [_]
  java.lang.Integer/SIZE)

(defmethod size-in-bytes java.lang.Long
  [_]
  java.lang.Long/SIZE)

(defmethod size-in-bytes java.lang.Double
  [_]
  java.lang.Double/SIZE)

(defmethod size-in-bytes (class (byte-array 1))
  [b]
  (count b))

(defmethod size-in-bytes clojure.lang.IPersistentMap
  [m]
  (+ (reduce + 0 (map size-in-bytes (keys m)))
     (reduce + 0 (map size-in-bytes (vals m)))))

(defmethod size-in-bytes clojure.lang.IPersistentCollection
  [coll]
  (reduce + 0 (map size-in-bytes coll)))

;; Implements the CmrCache protocol using an in memory cache store. The cache
;; data is saved in memory in a clojure.core.cache type in an atom
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
    (-> cache-atom
        (swap! (fn [cache]
                 (if (cc/has? cache key)
                   (cc/hit cache key)
                   ;; We don't do a miss here because the miss expects a value
                   ;; to be stored along with it. We can't use nil because it
                   ;; would end up storing nil with the key and potentially
                   ;; pushing out other valid values.
                   cache)))
        (get key)))

  (get-value
    [this key lookup-fn]
    (-> cache-atom
        (swap! (fn [cache]
                 (if (cc/has? cache key)
                   (cc/hit cache key)
                   (cc/miss cache key (lookup-fn)))))
        (get key)))

  (reset
    [this]
    (reset! cache-atom initial-cache))

  (set-value
    [this key value]
    (swap! cache-atom assoc key value))
  
  (cache-size
   [_]
   (reduce + 0 (map size-in-bytes (vals @cache-atom)))))

(record-pretty-printer/enable-record-pretty-printing InMemoryCache)

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Time To Live Cache
;;
;; This is a copy of the TTLCache from clojure core cache that uses the time
;; keeper to allow easier testing. From [org.clojure/core.cache "0.6.5"] (Not
;; Clojure itself)

(defn- key-killer
  [ttl expiry now]
  (let [ks (map key (filter #(> (- now (val %)) expiry) ttl))]
    #(apply dissoc % ks)))

(defcache TTLCache [cache ttl ttl-ms]
  CacheProtocol
  (lookup [this item]
    (let [ret (cc/lookup this item ::nope)]
      (when-not (= ::nope ret) ret)))
  (lookup [this item not-found]
    (if (cc/has? this item)
      (get cache item)
      not-found))
  (has? [_ item]
    (let [t (get ttl item (- ttl-ms))]
      (< (- (time-keeper/now-ms)
            t)
         ttl-ms)))
  (hit [this item] this)
  (miss [this item result]
    (let [now  (time-keeper/now-ms)
          kill-old (key-killer ttl ttl-ms now)]
      (TTLCache. (assoc (kill-old cache) item result)
                 (assoc (kill-old ttl) item now)
                 ttl-ms)))
  (seed [_ base]
    (let [now (time-keeper/now-ms)]
      (TTLCache. base
                 (into {} (for [x base] [(key x) now]))
                 ttl-ms)))
  (evict [_ key]
    (TTLCache. (dissoc cache key)
               (dissoc ttl key)
               ttl-ms))
  Object
  (toString [_]
    (str cache \, \space ttl \, \space ttl-ms)))

(defn ttl-cache-factory
  "Returns a TTL cache with the cache and expiration-table initialized to
  `base` --  each with the same time-to-live.

  This function also allows an optional `:ttl` argument that defines the
  default time in milliseconds that entries are allowed to reside in the
  cache."
  [base & {ttl :ttl :or {ttl 2000}}]
  {:pre [(number? ttl) (<= 0 ttl)
         (map? base)]}
  (clojure.core.cache/seed (TTLCache. {} {} ttl) base))

;; End of copy
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod create-core-cache :ttl
  [type value opts]
  (apply ttl-cache-factory value (flatten (seq opts))))

(defn create-in-memory-cache
  "Create in memory cache with different cache types for the internal cache.
  The currently supported cache types are :default, :lru, and :ttl.

  * :default type does not do cache evictions - cache items must be
    explicitly removed.
  * :lru (Least Recently Used) cache evicts items that have not been used
    recently when the cache size exceeds the threshold (default 32). This
    threshold can be set using the :threshold key in the opts parameter.
  * :ttl - cache evicts items that are older than a time-to-live threshold in
    milliseconds."
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
