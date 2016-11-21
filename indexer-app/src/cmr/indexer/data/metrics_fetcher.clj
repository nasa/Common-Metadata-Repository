(ns cmr.indexer.data.metrics-fetcher
  "Stores the latest community usage metrics json in a consistent cache."
  (:require
   [cmr.common.cache :as c]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.transmit.community-usage-metrics :as metrics]))

(defn- get-and-prepare-community-usage-metrics
  "Retrieve community usage metrics and cache with the collection short-name as the key
  and a list of version/access-count combos as the value to make lookups faster"
  [context]
  (when-let [metrics (seq (metrics/get-community-usage-metrics context))]
    (group-by :short-name metrics)))

(def usage-metrics-cache-key
  "The cache key to use when storing the metrics list in the system."
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
                 (get-and-prepare-community-usage-metrics context))))

(defn get-community-usage-metrics
  "Returns the community usage metrics. Community usage metrics come back as a list of Maps
  that contain collection short-name, version, and access-count.
  i.e.
  [{:short-name 'Coll1' :version '1' :access-count 100}
   {:short-name 'Coll2' :version 'N/A' :access-count 20}]"
  [context]
  (let [cache (c/context->cache context usage-metrics-cache-key)]
    (c/get-value cache
                 usage-metrics-cache-key
                 #(usage-metrics-cache-key context))))
