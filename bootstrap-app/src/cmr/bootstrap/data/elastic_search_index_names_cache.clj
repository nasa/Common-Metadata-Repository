(ns cmr.bootstrap.data.elastic-search-index-names-cache
  "Functions to support a cache that holds elastic search index data
  that is used when searching elastic search."
  (:require
   [cmr.common-app.services.search.elastic-search-index-names-cache :as elastic-search-index-names-cache]
   [cmr.common.hash-cache :as hcache]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [info]]
   [cmr.indexer.data.index-set :as indexer-index-set]
   [cmr.transmit.indexer :as indexer]))

(defn- get-collections-moving-to-separate-index
  "Returns a list of collections that are currently in the process of moving to a separate index.
  Takes a map with the keyword of the collection as the key and the target index as the value.
  For example: `{:C1-PROV1 \"separate-index\" :C2-PROV2 \"small-collections\"}` would return
               `[\"C1-PROV1\"]`"
  [rebalancing-targets-map]
  (keep (fn [[k v]]
          (when (= "separate-index" v)
            (name k)))
        rebalancing-targets-map))

(defn- fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  [context]
  (let [fetched-index-set (indexer/get-index-set context indexer-index-set/index-set-id)
        ;; We want to make sure collections in the process of being moved to a separate granule
        ;; index continue to use the small collections index for search.
        rebalancing-targets-map (get-in fetched-index-set [:index-set :granule :rebalancing-targets])
        moving-to-separate-index (get-collections-moving-to-separate-index rebalancing-targets-map)
        index-names-map (get-in fetched-index-set [:index-set :concepts])]
    {:index-names index-names-map
     :rebalancing-collections moving-to-separate-index}))

(defn refresh-index-names-cache
  "Refresh the search index-names cache."
  [system]
  (info "Refreshing search index-names cache.")
  (let [context {:system system}
        index-names-map (fetch-concept-type-index-names context)
        index-names (:index-names index-names-map)
        cache (hcache/context->cache context elastic-search-index-names-cache/index-names-cache-key)]
    (hcache/set-values cache elastic-search-index-names-cache/index-names-cache-key index-names)
    (hcache/set-value cache elastic-search-index-names-cache/index-names-cache-key :rebalancing-collections (:rebalancing-collections index-names-map))
    (info "Refreshed search index-names cache.")))

;; A job for refreshing the index names cache.
(defjob RefreshIndexNamesCacheJob
  [ctx system]
  (refresh-index-names-cache system))

(defn refresh-index-names-cache-job
  [job-key]
  {:job-type RefreshIndexNamesCacheJob
   :job-key job-key
   ;; The time here is UTC. The indexes only change when a new concept is introduced or
   ;; when rebalancing happens which is a manual process and doesn't happen often.
   ;; When rebalancing happens, the operator can refresh the cache to capture the data
   ;; during and after a rebalance. An automated way would be to publish when a rebalance happens.
   :daily-at-hour-and-minute [07 00]})

(comment
  ;; load up the bootstrap system and refresh the cache.
  (let [system (cmr.bootstrap.system/create-system)]
    (refresh-index-names-cache system)))
