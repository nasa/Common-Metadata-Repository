(ns cmr.search.services.acls.collections-cache
  "This is a cache of collection data for helping enforce granule acls in an efficient manner"
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.jobs :refer [defjob]]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.cache :as cache]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.search.models.query :as q]
            [cmr.search.services.query-execution :as qe]
            [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]))

(def initial-cache-state
  "The initial cache state."
  nil)

(defn create-cache
  "Creates a new empty collections cache."
  []
  (mem-cache/create-in-memory-cache))

(def cache-key
  :collections-for-gran-acls)

(defn- fetch-collections
  "Executes a query that will fetch all of the collection information needed for caching."
  [context]
  (let [result-processor (fn [_ _ elastic-item]
                           (assoc (acl-rhh/parse-elastic-item :collection elastic-item)
                                  :concept-id (:_id elastic-item)))
        query (q/query {:concept-type :collection
                        :condition q/match-all
                        :skip-acls? true
                        :page-size :unlimited
                        :result-format :query-specified
                        :fields (cons :concept-id acl-rhh/collection-elastic-fields)
                        :result-features {:query-specified {:result-processor result-processor}}})]
    (:items (qe/execute-query context query))))

(defn- fetch-collections-map
  "Retrieve collections from search and return a map by concept-id and provider-id"
  [context]
  (let [collections (fetch-collections context)
        by-concept-id (into {} (for [{:keys [concept-id] :as coll} collections]
                                 [concept-id coll]))
        by-provider-id-entry-title (into {}
                                         (for [{:keys [provider-id entry-title] :as coll}
                                               collections]
                                           [[provider-id entry-title] coll]))]
    ;; We could reduce the amount of memory here if needed by only fetching the collections that
    ;; have granules.
    {:by-concept-id by-concept-id
     :by-provider-id-entry-title by-provider-id-entry-title}))

(defn refresh-cache
  "Refreshes the collections stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching collections. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [cache (cache/context->cache context cache-key)
        collections-map (fetch-collections-map context)]
    (cache/set-value cache :collections collections-map)))

(defn- get-collections-map
  "Gets the cached value."
  [context]
  (let [coll-cache (cache/context->cache context cache-key)
        collection-map (cache/get-value
                         coll-cache
                         :collections
                         (fn [] (fetch-collections-map context)))]
    (if (empty? collection-map)
      (errors/internal-error! "Collections were not in cache.")
      collection-map)))

(defn get-collection
  "Gets a single collection from the cache by concept id. Handles refreshing the cache if it is not found in it.
  Also allows provider-id and entry-title to be used."
  ([context concept-id]
   (let [by-concept-id (:by-concept-id (get-collections-map context))]
     (when-not (by-concept-id concept-id)
       (info (format "Collection with id %s not found in cache. Manually triggering cache refresh"
                     concept-id))
       (refresh-cache context))
     (get (:by-concept-id (get-collections-map context)) concept-id)))
  ([context provider-id entry-title]
   (let [by-provider-id-entry-title (:by-provider-id-entry-title (get-collections-map context))]
     (get by-provider-id-entry-title [provider-id entry-title]))))

(defjob RefreshCollectionsCacheForGranuleAclsJob
  [ctx system]
  (refresh-cache {:system system}))

(def refresh-collections-cache-for-granule-acls-job
  {:job-type RefreshCollectionsCacheForGranuleAclsJob
   ;; 15 minutes
   :interval (* 15 60)})