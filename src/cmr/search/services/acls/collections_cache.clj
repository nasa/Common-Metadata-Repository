(ns cmr.search.services.acls.collections-cache
  "This is a cache of collection data for helping enforce granule acls in an efficient manner"
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.jobs :refer [defjob]]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.search.models.query :as q]
            [cmr.search.services.query-execution :as qe]))

(def initial-cache-state
  "The initial cache state. This will be a map of collection concept ids to the maps containing
  colleciton information"
  {})

(defn create-cache
  "Creates a new empty collections cache. The cache itself is just an atom with a map."
  []
  (atom initial-cache-state))

(def cache-key
  :collections-for-gran-acls)

(defn- context->cache
  "Gets the collections cache from the context"
  [context]
  (get-in context [:system :caches cache-key]))

(defn reset
  "Resets the cache back to it's initial state"
  [context]
  (-> context
      context->cache
      (reset! initial-cache-state)))

(defn fetch-collections
  [context]
  (let [query (q/query {:concept-type :collection
                        :condition (q/->MatchAllCondition)
                        :skip-acls? true
                        :page-size :unlimited
                        :result-format :query-specified
                        :fields [:entry-title :access-value]})]
    (:items (qe/execute-query context query))))

(defn refresh-cache
  "Refreshes the collections stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching collections. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [cache-atom (context->cache context)
        collections (fetch-collections context)
        collections-map (into {} (for [{:keys [concept-id] :as coll} collections]
                                   [concept-id coll]))]
    (reset! cache-atom collections-map)))

(defn get-collections-map
  "Gets the cached value."
  [context]
  (let [cache-atom (context->cache context)]
    (when-not @cache-atom
      (info "No collections for granule acls found in cache. Manually triggering cache refresh")
      (refresh-cache context))
    (if-let [collections-map @cache-atom]
      collections-map
      (errors/internal-error! "Collections were not in cache."))))

(defn get-collection
  "Gets a single collection from the cache. Handles refreshing the cache if it is not found in it"
  [context concept-id]
  (let [collections-map (get-collections-map context)]
    (when-not (collections-map concept-id)
      (info (format "Collection with id %s not found in cache. Manually triggering cache refresh"
                    concept-id))
      (refresh-cache context))
    (get (get-collections-map context) concept-id)))

(defjob RefreshCollectionsCacheForGranuleAclsJob
  [ctx system]
  (refresh-cache {:system system}))

(def refresh-collections-cache-for-granule-acls-job
  {:job-type RefreshCollectionsCacheForGranuleAclsJob
   :interval 3600})