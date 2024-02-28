(ns cmr.redis-utils.redis-hash-cache
  "An implementation of the CMR hash cache protocol on top of Redis. Mutliple applications
  have multiple caches which use one instance of Redis. Therefore calling reset on
  a Redis cache should not delete all of the data stored. Instead when creating a Redis
  cache the caller must provide a list of keys which should be deleted when calling
  reset on that particular cache. TTL MUST be set if you expect your keys to be evicted automatically."
  (:require
   [cmr.common.hash-cache :as cache]
   [cmr.redis-utils.redis-cache :as rc]
   [cmr.redis-utils.redis :as redis :refer [wcar*]]
   [taoensso.carmine :as carmine]))

;; Implements the CmrHashCache protocol by saving data in Redis.
(defrecord RedisHashCache
  [
   ;; A collection of keys used by this cache. Only these keys will be deleted from the backend
   ;; store on calls to reset.
   keys-to-track

   ;; The time to live for the key in seconds.
   ttl

   ;; Refresh the time to live when GET operations are called on key.
   refresh-ttl?]

  cache/CmrHashCache
  (get-map
    [this key]
    ;; key is the cache-key 
    ;; hgetall returns a vector structure [[field1 value1 field2 value2 fieldN valueN]]
    ;; First pulls out the inner vector then conver it to {field value field value} hash 
    ;; map so that callers can process it.
    (let [s-key (rc/serialize key)
          result (-> (wcar* s-key true :as-pipeline (carmine/hgetall s-key))
                     first)]
      (when-not (or (nil? result)
                    (empty? result))
        (into {} (for [[a b] (partition 2 result)]
                   {a b})))))

  (key-exists
    [this key]
    ;; key is the cache-key. Retuns true if the cache key exists in redis nil otherwise
    (let [s-key (rc/serialize key)
          exists (wcar* s-key true (carmine/exists s-key))]
      (when exists
        (> exists 0))))

  (get-keys
    [this key]
    ;; key is the cache-key 
    ;; hkeys returns a vector structure [[key1 key2 ... keyn]] First pulls out the inner vector
    ;; returns a vector of keys.
    (let [s-key (rc/serialize key)]
      (-> (wcar* s-key true :as-pipeline (carmine/hkeys s-key))
          first)))

  (get-value
    [this key field]
    ;; key is the cache-key. Returns the value of the passed in field.
    (let [s-key (rc/serialize key)]
      (-> (wcar* s-key true :as-pipeline (carmine/hget s-key field))
          first)))
  
  (get-values
    ;; key is the cache-key. Fields is either a vector or a list of fields.
    ;; returns a vector of values.
    [this key fields]
    (let [s-key (rc/serialize key)]
      (map #(-> (wcar* s-key true :as-pipeline (carmine/hget s-key %1))
                first)
           fields)))

  (reset
    [this]
    (doseq [the-key keys-to-track]
      (let [s-key (rc/serialize the-key)]
        (wcar* s-key false (carmine/del s-key)))))

  (reset
    [this key]
    (let [s-key (rc/serialize key)]
      (wcar* s-key false (carmine/del s-key))))

  (set-value
    [this key field value]
    (let [s-key (rc/serialize key)]
      ;; Store value in map to aid deserialization of numbers.
      (wcar* s-key false (carmine/hset s-key field value))))
  
  (set-values
    [this key field-value-map]
    (let [s-key (rc/serialize key)]
      (doall (map #(wcar* s-key false (carmine/hset s-key %1 (get field-value-map %1)))
                  (keys field-value-map)))))

  (cache-size
    [this key]
    (let [s-key (rc/serialize key)]
      ;; Return 0 if the cache is empty or does not yet exist. This is for cmr.common-app.services.cache-info.
      (if-let [size (wcar* s-key true (carmine/memory-usage s-key))]
        size
        0))))

(defn create-redis-hash-cache
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
  ([]
   (create-redis-hash-cache nil))
  ([options]
   (->RedisHashCache (:keys-to-track options)
                     (get options :ttl)
                     (get options :refresh-ttl? false))))
