(ns cmr.search.services.acls.collections-cache
  "This is a cache of collection data for helping enforce granule acls in an efficient manner"
  (:require
   [clojure.walk :as walk]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as q-mod]
   [cmr.common.date-time-parser :as time-parser]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.redis-utils.redis-hash-cache :as red-hash-cache]
   [cmr.common-app.services.search.acl-results-handler-helper :as acl-rhh]
   [cmr.search.services.messages.collections-cache-messages :as coll-msg]
   [cmr.common-app.data.search.collection-for-gran-acls-caches :as coll-for-gran-acl-caches]))

;; added to common lib will remove in future
(def cache-key
  "This is the key name that will show up in redis.
   Note: No other file reads this cache, so it is functionally private."
  :collections-for-gran-acls)

;; added to common lib will remove in future
(def initial-cache-state
  "The initial cache state."
  nil)

;; added to common lib will remove in future
(def job-refresh-rate
  "This is the frequency that the cron job will run. It should be less then
   coll-for-gran-acl-ttl"
  ;; 15 minutes
  (* 60 15))

;; added to common lib will remove in future
(def coll-for-gran-acl-ttl
  "This is when Redis should expire the data, this value should never be hit if
   the cron job is working correctly, however in some modes (such as local dev
   with no database) it may come into play."
  ;; 30 minutes
  (* 60 30))

;; added to common lib will remove in future
(defn create-cache
  "Creates a new empty collections cache."
  []
  (red-hash-cache/create-redis-hash-cache {:keys-to-track [:collections-for-gran-acls]
                                           :ttl coll-for-gran-acl-ttl}))

;; added to common lib will remove in future
(defn clj-times->time-strs
  "Take a map and convert any date objects into strings so the map can be cached.
   This can be reversed with time-strs->clj-times."
  [data]
  (walk/postwalk
   #(if (true? (instance? org.joda.time.DateTime %))
      (time-parser/clj-time->date-time-str %)
      %)
   data))

;; added to common lib will remove in future
(defn time-strs->clj-times
  "Take a map which has string dates and convert them to DateTime objects. This
   can be reversed with clj-times->time-strs."
  [data]
  (walk/postwalk
   #(if-let [valid-date (time-parser/try-parse-datetime %)] valid-date %)
   data))

;; added to common lib will remove in future
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
        query (q-mod/query {:concept-type :collection
                            :condition q-mod/match-all
                            :skip-acls? true
                            :page-size :unlimited
                            :result-format :query-specified
                            :result-fields (cons :concept-id acl-rhh/collection-elastic-fields)
                            :result-features {:query-specified {:result-processor result-processor}}})]
    (:items (qe/execute-query context query))))

;; added to common lib will remove in future
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

;; added to common lib will remove in future
(defn refresh-cache
  "Refreshes the collections stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching collections. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [cache (hash-cache/context->cache context cache-key)
        collections-map (fetch-collections-map context)]
    (doseq [[coll-key coll-value] collections-map]
      (hash-cache/set-value cache cache-key coll-key (clj-times->time-strs coll-value)))))

;; TODO this is the new func that will replace 'get-collection' AND 'get-collection-map' during transition
(defn get-collection-gran-acls
 "Gets a single collection from the cache by concept id. Handles refreshing the cache if it is not found in it.
 Also allows provider-id and entry-title to be used."
 ([context coll-concept-id]
  (let [coll-by-concept-id-cache (hash-cache/context->cache context coll-for-gran-acl-caches/coll-by-concept-id-cache-key)
        collection (hash-cache/get-value
                    coll-by-concept-id-cache
                    coll-for-gran-acl-caches/coll-by-concept-id-cache-key
                    coll-concept-id)]
   (when (or (nil? collection) (empty? collection))
    (info (coll-msg/collection-not-found coll-concept-id))
    (coll-for-gran-acl-caches/refresh-entire-cache context)) ;;TODO replace
   (time-strs->clj-times collection)))
 ([context provider-id entry-title]
  (let [coll-by-provider-id-and-entry-id-cache (hash-cache/context->cache context coll-for-gran-acl-caches/coll-by-provider-id-and-entry-title-cache-key)
        collection (hash-cache/get-value
                    coll-by-provider-id-and-entry-id-cache ;; cache
                    coll-for-gran-acl-caches/coll-by-provider-id-and-entry-title-cache-key ;; key
                    (str provider-id entry-title))]
   (when (or (nil? collection) (empty? collection))
    (info (coll-msg/collection-not-found (str provider-id entry-title)))
    (coll-for-gran-acl-caches/refresh-entire-cache context)) ;;TODO replace
   (time-strs->clj-times collection))))

;; TODO remove after transition
(defn- get-collections-map
  "Gets the cached value. By default an error is thrown if there is no value in
   the cache. Pass in anything other then :return-errors for the third parameter
   to have null returned when no data is in the cache. :return-errors is for the
   default behavior."
  ([context key-type]
   (get-collections-map context key-type :return-errors))
  ([context key-type throw-errors]
  (let [coll-cache (hash-cache/context->cache context cache-key)
        collection-map (hash-cache/get-value
                        coll-cache
                        cache-key
                        key-type)]
    (if (and (= :return-errors throw-errors) (empty? collection-map))
      (errors/internal-error! (coll-msg/collections-not-in-cache key-type))
      (time-strs->clj-times collection-map)))))

;; TODO remove after transition
(defn get-collection
  "Gets a single collection from the cache by concept id. Handles refreshing the cache if it is not found in it.
  Also allows provider-id and entry-title to be used."
  ([context concept-id]
   (let [by-concept-id (get-collections-map context :by-concept-id :no-errors)]
     (when-not (and by-concept-id (by-concept-id concept-id))
       (info (coll-msg/collection-not-found concept-id))
       (refresh-cache context))
     (get (get-collections-map context :by-concept-id ) concept-id)))
  ([context provider-id entry-title]
   (let [by-provider-id-entry-title (get-collections-map context :by-provider-id-entry-title )]
     (get by-provider-id-entry-title [provider-id entry-title]))))

;; added to common lib will remove in future
(defjob RefreshCollectionsCacheForGranuleAclsJob
  [ctx system]
  (refresh-cache {:system system}))

;; added to common lib will remove in future
(def refresh-collections-cache-for-granule-acls-job
  {:job-type RefreshCollectionsCacheForGranuleAclsJob
   :interval job-refresh-rate})
