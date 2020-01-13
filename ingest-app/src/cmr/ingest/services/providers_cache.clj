(ns cmr.ingest.services.providers-cache
  "Defines functions for creating, configuring and retrieving values from providers cache.
  The providers cache is a consistent cache with only one key :providers and all providers as its value."
  (:require [cmr.common.cache :as cache]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.ingest.services.provider-service :as ps]
            [cmr.transmit.cache.consistent-cache :as consistent-cache]))

(def providers-cache-key
  "The cache key for the providers cache."
  :providers)

(def providers-keys-to-track
  "The collection of keys which should be deleted from redis whenever someone attempts to clear the
  providers cache."
  [":providers-hash-code"])

(defconfig providers-cache-time-seconds
  "The number of seconds providers information will be cached."
  {:default 1800 :type Long})

(defn create-providers-cache
  "Creates a cache for providers."
  []
  (consistent-cache/create-consistent-cache
   {:ttl (providers-cache-time-seconds)
    :keys-to-track providers-keys-to-track}))

(defn get-providers-from-cache
  [context]
  (let [cache (cache/context->cache context providers-cache-key)]
    (cache/get-value cache providers-cache-key (partial ps/get-providers context))))
