(ns cmr.redis-utils.redis-cache
  "An implementation of the CMR cache protocol on top of Redis. Mutliple applications
  have multiple caches which use one instance of Redis. Therefore calling reset on
  a Redis cache should not delete all of the data stored. Instead when creating a Redis
  cache the caller must provide a list of keys which should be deleted when calling
  reset on that particular cache. TTL MUST be set if you expect your keys to be evicted automatically."
  (:require
   [clojure.edn :as edn]
   [cmr.common.cache :as cache]
   [cmr.redis-utils.redis :as redis :refer [wcar*]]
   [taoensso.carmine :as carmine]))

(defn serialize
  "Serializes v."
  [v]
  (pr-str v))

(defn deserialize
  "Deserializes v."
  [v]
  (when v (edn/read-string v)))

;; Implements the CmrCache protocol by saving data in Redis.
(defrecord RedisCache
  [
   ;; A collection of keys used by this cache. Only these keys will be deleted from the backend
   ;; store on calls to reset.
   keys-to-track

   ;; The time to live for the key in seconds.
   ttl

   ;; Refresh the time to live when GET operations are called on key.
   refresh-ttl?

   ;; The connection used to get data from redis. This can either be the redis read-only replicas
   ;; or the primary node.
   read-connection

   ;; The connection used to write data to redis, this is the primary node.
   primary-connection]

  cache/CmrCache
  (get-keys
    [_this]
    (map deserialize (redis/get-keys read-connection)))

  (key-exists
    [_this key]
    ;; key is the cache-key. Returns true if the cache key exists in redis, otherwise returns nil.
    (let [exists (wcar* key true read-connection (carmine/exists (serialize key)))]
      (when exists
        (> exists 0))))

  (get-value
    [_this key]
    (let [s-key (serialize key)]
      (-> (wcar* key
                 true
                 read-connection
                 :as-pipeline
                 (carmine/get s-key)
                 (when refresh-ttl? (carmine/expire s-key ttl)))
          first
          :value)))

  (get-value
    [this key lookup-fn]
    (let [cache-value (cache/get-value this key)]
      (if-not (nil? cache-value)
        cache-value
        (let [value (lookup-fn)]
          (cache/set-value this key value)
          value))))

  (reset
    [_this]
    (doseq [the-key keys-to-track]
      (wcar* the-key false primary-connection (carmine/del (serialize the-key)))))

  (set-value
    [_this key value]
    ;; Store value in map to aid deserialization of numbers.
    (let [s-key (serialize key)]
      (wcar* s-key false primary-connection (carmine/set s-key {:value value})
             (when ttl (carmine/expire s-key ttl)))))

  (cache-size
    [_]
    (reduce #(+ %1 (if-let [size (wcar* (serialize %2) true read-connection (carmine/memory-usage (serialize %2)))]
                     size
                     0))
            0
            keys-to-track)))

(defn create-redis-cache
  "Creates an instance of the redis cache.
  options:
      :keys-to-track
       The keys that are to be managed by this cache.
      :ttl
       The time to live for the key in seconds. If nil assumes key will never expire. NOTE:
       The key is not guaranteed to stay in the cache for up to ttl. If the
       cache becomes full any key that is not set to persist will
       be a candidate for eviction.
      :refresh-ttl?
       When a GET operation is called called on the key then the ttl is refreshed
       to the time to live set by the initial cache."
  [options]
  (->RedisCache (:keys-to-track options)
                (:ttl options)
                (get options :refresh-ttl? false)
                (:read-connection options)
                (:primary-connection options)))
