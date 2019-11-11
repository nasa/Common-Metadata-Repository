(ns cmr.redis-utils.redis-cache
  "An implementation of the CMR cache protocol on top of Redis. Mutliple applications
  have multiple caches which use one instance of Redis. Therefore calling reset on
  a Redis cache should not delete all of the data stored. Instead when creating a Redis
  cache the caller must provide a list of keys which should be deleted when calling
  reset on that particular cache."
  (:require
   [clojure.edn :as edn]
   [cmr.common.cache :as cache]
   [cmr.redis-utils.redis :refer [wcar*]]
   [taoensso.carmine :as carmine]))

(defn- serialize
  "Serializes v."
  [v]
  (pr-str v))

(defn- deserialize
  "Deserializes v."
  [v]
  (when v (edn/read-string v)))

;; Implements the CmrCache protocol by saving data in Redis
(defrecord RedisCache
  [
   ;; A collection of keys used by this cache. Only these keys will be deleted from the backend
   ;; store on calls to reset
   keys-to-track]

  cache/CmrCache
  (get-keys
    [this]
    (map deserialize (wcar* (carmine/keys "*"))))

  (get-value
    [this key]
    (:value (wcar* (carmine/get (serialize key)))))

  (get-value
    [this key lookup-fn]
    (let [cache-value (cache/get-value this key)]
      (if-not (nil? cache-value)
        cache-value
        (let [value (lookup-fn)]
          (cache/set-value this key value)
          value))))

  (reset
    [this]
    (doseq [the-key keys-to-track]
      (wcar* (carmine/del (serialize the-key)))))

  (set-value
    [this key value]
    ;; Store value in map to aid deserialization of numbers.
    (wcar* (carmine/set (serialize key) {:value value}))))

(defn create-redis-cache
  "Creates an instance of the redis cache."
  ([]
   (create-redis-cache nil))
  ([options]
   (->RedisCache (:keys-to-track options))))
