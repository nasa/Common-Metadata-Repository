(ns cmr.elastic-utils.search.es-index-name-cache
  "Implements a cache to store Elasticsearch information used when searching elastic search for
   concepts."
  (:require
   [cmr.common.hash-cache :as hcache]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [info]]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as rhcache]
   [cmr.transmit.indexer :as indexer]))

;; id of the index-set that CMR is using, hard code for now
(def index-set-id 1)

(def index-names-cache-key
  "The name of the cache for caching index names. It will contain a map of concept type to a map of
   index names to the name of the index used in elasticsearch.

   Example:
   {:granule {:small_collections \"1_small_collections\"},
    :tag {:tags \"1_tags\"},
    :collection {:all-collection-revisions \"1_all_collection_revisions\",
                 :collections \"1_collections_v2\"}}"
  :index-names)

(defn create-index-cache
  "Used to create the cache that will be used for caching index names."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [index-names-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)}))

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
  (let [fetched-index-set (indexer/get-index-set context index-set-id)
        ;; We want to make sure collections in the process of being moved to a separate granule
        ;; index continue to use the small collections index for search.
        rebalancing-targets-map (get-in fetched-index-set
                                        [:index-set
                                         :granule
                                         :rebalancing-targets])
        moving-to-separate-index (get-collections-moving-to-separate-index rebalancing-targets-map)
        index-names-map (get-in fetched-index-set [:index-set :concepts])]
    {:index-names index-names-map
     :rebalancing-collections moving-to-separate-index}))

(defn refresh-index-names-cache
  "Refresh the search index-names cache."
  [context]
  (info "Refreshing search index-names cache.")
  (let [index-names-map (fetch-concept-type-index-names context)
        index-names (:index-names index-names-map)
        cache (hcache/context->cache context index-names-cache-key)]
    (info (str "Refreshing search index-names cache - index-names: " index-names))
    (hcache/set-values cache index-names-cache-key index-names)
    (hcache/set-value cache
                      index-names-cache-key
                      :rebalancing-collections
                      (:rebalancing-collections index-names-map))
    (info "Refreshed search index-names cache.")))

(declare ctx system)

;; A job for refreshing the index names cache.
(defjob RefreshIndexNamesCacheJob
  [_ctx system]
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
