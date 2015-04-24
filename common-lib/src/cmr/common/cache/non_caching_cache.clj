(ns cmr.common.cache.non-caching-cache
  "Implements the CMR cache protocol but caches nothing"
  (:require [cmr.common.cache :as c]))

(defrecord NonCachingCache
  []

  c/CmrCache
  (get-keys
    [this]
    )

  (get-value
    [this key]
    )

  (get-value
    [this key lookup-fn]
    (lookup-fn))

  (reset
    [this]
    )

  (set-value
    [this key value]
    ))

(def non-caching-cache
  "An instance of the NonCachingCache"
  (->NonCachingCache))