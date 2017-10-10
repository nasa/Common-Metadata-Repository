(ns cmr.common-app.cache.cubby-cache
  "An implementation of the CMR cache protocol on top of the cubby application. Cubby only supports
  persistence and returning strings so this automatically serializes and deserializes the keys and
  values with EDN. Any key or value serializable to EDN is supported. Operationally cubby uses
  Elasticsearch as a backend store, and multiple applications have multiple caches which use cubby.
  Therefore calling reset on a cubby cache should not delete all of the data stored in the backend.
  Instead when creating a cubby cache the caller must provide a list of keys which should be deleted
  when calling reset on that particular cache."
  (:require
   [clojure.edn :as edn]
   [cmr.common.cache :as c]
   [cmr.transmit.config :as config]
   [cmr.transmit.cubby :as cubby]))

(defn- serialize
  "Serializes a value for storage in cubby."
  [v]
  (pr-str v))

(defn- deserialize
  "Deserializes a stored value from cubby"
  [v]
  (when v (edn/read-string v)))

;; Implements the CmrCache protocol by saving data in the cubby application
(defrecord CubbyCache
  [;; A context containing a connection to cubby
   context-with-conn

   ;; A collection of keys used by this cache. Only these keys will be deleted from the backend
   ;; store on calls to reset
   keys-to-track]

  c/CmrCache
  (get-keys
    [this]
    (map deserialize (cubby/get-keys context-with-conn)))

  (get-value
    [this key]
    (deserialize (cubby/get-value context-with-conn (serialize key))))

  (get-value
    [this key lookup-fn]
    (let [c-value (c/get-value this key)]
      (if-not (nil? c-value)
        c-value
        (let [value (lookup-fn)]
          (c/set-value this key value)
          value))))

  (reset
    [this]
    (doseq [the-key keys-to-track]
      (cubby/delete-value context-with-conn (serialize the-key))))

  (set-value
    [this key value]
    (cubby/set-value context-with-conn (serialize key) (serialize value))))

(defn create-cubby-cache
  "Creates an instance of the cubby cache."
  ([]
   (create-cubby-cache nil))
  ([options]
   (->CubbyCache {:system (config/system-with-connections {} [:cubby])}
                 (:keys-to-track options))))
