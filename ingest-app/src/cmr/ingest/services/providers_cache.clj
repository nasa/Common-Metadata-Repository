(ns cmr.ingest.services.providers-cache
  "Defines functions for creating, configuring and retrieving values from providers cache.
  The providers cache is a TTL cache with only one key :providers and all providers as its value."
  (:require [cmr.common.cache :as cache]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.ingest.services.provider-service :as ps]))

(def providers-cache-key
  "The cache key for the providers cache."
  :providers)

(defconfig providers-cache-time-seconds
  "The number of seconds providers information will be cached."
  {:default 1800 :type Long})

(defn create-providers-cache
  "Creates a cache for providers."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl (* 1000 (providers-cache-time-seconds))}))

(defn get-providers-from-cache
  [context]
  (let [cache (cache/context->cache context providers-cache-key)]
    (cache/get-value cache providers-cache-key (partial ps/get-providers context))))
