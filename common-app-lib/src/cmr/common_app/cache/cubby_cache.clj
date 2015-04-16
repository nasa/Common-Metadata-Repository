(ns cmr.common-app.cache.cubby-cache
  "An implementation of the CMR cache protocol on top of the cubby application. Cubby only supports
  persistence and returning strings so this automatically serializes and deserializes the keys and
  values with EDN. Any key or value serializable to EDN is supported."
  (:require [cmr.common.cache :as c]
            [cmr.transmit.cubby :as cubby]
            [cmr.transmit.config :as config]
            [clojure.edn :as edn]))

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
   ]

  c/CmrCache
  (get-keys
    [this]
    (map deserialize (cubby/get-keys context-with-conn)))

  (get-value
    [this key]
    (deserialize (cubby/get-value context-with-conn (serialize key))))

  (get-value
    [this key lookup-fn]
    (or (c/get-value this key)
        (let [value (lookup-fn)]
          (c/set-value this key value)
          value)))

  (reset
    [this]
    (cubby/delete-all-values context-with-conn))

  (set-value
    [this key value]
    (cubby/set-value context-with-conn (serialize key) (serialize value))))

(defn create-cubby-cache
  "Creates an instance of the cubby cache."
  []
  (->CubbyCache {:system (config/system-with-connections {} [:cubby])}))


