(ns cmr.indexer.data.metrics-fetcher
  "Stores the latest community usage metrics json in a consistent cache."
  (:require
   [cmr.common.cache :as c]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.transmit.community-usage-metrics :as metrics]))

(def usage-metrics-cache-key
  "The cache key to use when storing with caches in the system."
  :usage-metrics-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  (stl-cache/create-single-thread-lookup-cache))  

(defn refresh-cache
  "Refreshes the community usage metrics in the cache."
  [context]
  (let [cache (c/context->cache context usage-metrics-cache-key)]
    (c/set-value cache usage-metrics-cache-key
                 (metrics/get-community-usage-metrics context))))

(defn get-community-usage-metrics
  "Returns the community usage metrics."
  [context]
  (let [cache (c/context->cache context usage-metrics-cache-key)]
    (c/get-value cache
                 usage-metrics-cache-key
                 #(usage-metrics-cache-key context))))
