(ns cmr.search.services.acls.collections-cache
  "This is a cache of collection data for helping enforce granule acls in an efficient manner"
  (:require
   [clojure.walk :as walk]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as q]
   [cmr.common.cache :as cache]
   [cmr.common.date-time-parser :as time-parser]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]))

;; No other file reads this cache
(def cache-key
  :collections-for-gran-acls)

(def initial-cache-state
  "The initial cache state."
  nil)

(def job-refresh-rate
  "This is the frequency that the cron job will run. It should be less then
   coll-for-gran-acl-ttl"
  ;; 15 minutes
  (* 15 60))

(def coll-for-gran-acl-ttl
  "This is when Redis should expire the data, this value should never be hit if
   the cron job is working correctly, however in some modes (such as local dev
   with no database) it may come into play."
  ;; 30 minutes
  (* 30 60))

(defn create-cache
  "Creates a new empty collections cache."
  []
  (redis-cache/create-redis-cache {:keys-to-track [:collections-for-gran-acls]
                                   :ttl coll-for-gran-acl-ttl}))

(defn clj-times->time-strs
  "Take a map and convert any date objects into strings so the map can be cached.
   This can be reversed with time-strs->clj-times."
  [data]
  (walk/postwalk
   #(if (true? (instance? org.joda.time.DateTime %))
      (time-parser/clj-time->date-time-str %)
      %)
   data))

(defn time-strs->clj-times
  "Take a map which has string dates and convert them to DateTime objects. This
   can be reversed with clj-times->time-strs."
  [data]
  (walk/postwalk
   #(if-let [valid-date (time-parser/try-parse-datetime %)] valid-date %)
   data))

(defn- fetch-collections
  "Executes a query that will fetch all of the collection information needed for caching."
  [context]
  ;; when creating a result processor, realize all the lazy (delay) values to
  ;; actual values so that the resulting object can be cached in redis or some
  ;; other text based caching system and not clojure memory
  (let [result-processor (fn [_ _ elastic-item]
                           (assoc (util/delazy-all (acl-rhh/parse-elastic-item
                                                    :collection
                                                    elastic-item))
                                  :concept-id (:_id elastic-item)))
        query (q/query {:concept-type :collection
                        :condition q/match-all
                        :skip-acls? true
                        :page-size :unlimited
                        :result-format :query-specified
                        :result-fields (cons :concept-id acl-rhh/collection-elastic-fields)
                        :result-features {:query-specified {:result-processor result-processor}}})]
    (:items (qe/execute-query context query))))

(defn- fetch-collections-map
  "Retrieve collections from search and return a map by concept-id and provider-id"
  [context]
  (let [collections (fetch-collections context)
        by-concept-id (into {} (for [{:keys [concept-id] :as coll} collections]
                                 [concept-id coll]))
        by-provider-id-entry-title (into {}
                                         (for [{:keys [provider-id EntryTitle] :as coll}
                                               collections]
                                           [[provider-id EntryTitle] coll]))]
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
    (cache/set-value cache cache-key (clj-times->time-strs collections-map))))

(defn- get-collections-map
  "Gets the cached value."
  [context]
  (let [coll-cache (cache/context->cache context cache-key)
        collection-map (cache/get-value
                        coll-cache
                        cache-key
                        (fn [] (clj-times->time-strs (fetch-collections-map context))))]
    (if (empty? collection-map)
      (errors/internal-error! "Collections were not in cache.")
      (time-strs->clj-times collection-map))))

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
   :interval job-refresh-rate})
