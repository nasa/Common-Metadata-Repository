(ns cmr.common-app.data.search.collection-for-gran-acls-by-concept-id-cache
"Defines common functions and defs a cache for search collections.
 The collections cache contains data like the following:

 concept-id -> {collection info}"

(:require
  [cmr.redis-utils.redis-hash-cache :as red-hash-cache]
  [cmr.common.hash-cache :as hash-cache]
  [cmr.common.jobs :refer [defjob]]
  [cmr.common.log :as log :refer (debug info warn error)]
  [cmr.common.services.errors :as errors]))

(def cache-key
  "Identifies the key used when the cache is stored in the system."
  :collections-for-gran-acls-by-concept-id)

(def initial-cache-state
  "The initial cache state."
  nil)

(def job-refresh-rate
  "This is the frequency that the cron job will run. It should be less than coll-for-gran-acl-by-concept-id-ttl"
  ;; 15 minutes
  (* 60 15))

(def cache-ttl
  "This is when Redis should expire the data, this value should never be hit if
   the cron job is working correctly, however in some modes (such as local dev
   with no database) it may come into play."
   ;; 30 minutes
  (* 60 30))

(defn create-cache
  "Creates a new empty collections cache."
  []
  (red-hash-cache/create-redis-hash-cache {:keys-to-track [cache-key]
                                           :ttl           cache-ttl}))
;; FIXME
(defn refresh-cache
  "Refreshes the collections-for-gran-acls collection-by-concept-id-cache stored in the cache.
  This should be called from a background job on a timer to keep the cache fresh.
  This will throw an exception if there is a problem fetching collections.
  The caller is responsible for catching and logging the exception."
  [context]
  (let [collection-by-concept-id-cache (hash-cache/context->cache context cache-key)
        collections-map (fetch-collections-map context)] ;; TODO redo this logic
   (doseq [[coll-key coll-value] collections-map]
    (hash-cache/set-value collection-by-concept-id-cache cache-key coll-key (clj-times->time-strs coll-value)))))
(defjob RefreshCollectionsCacheForGranuleAclsByConceptIdJob
        [ctx system]
        (refresh-cache {:system system}))
(def refresh-collections-cache-for-granule-acls-by-concept-id-job
  {:job-type RefreshCollectionsCacheForGranuleAclsByConceptIdJob
   :interval job-refresh-rate})