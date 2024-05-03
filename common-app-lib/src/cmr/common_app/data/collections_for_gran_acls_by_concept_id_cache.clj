(ns cmr.common-app.data.collections-for-gran-acls-by-concept-id-cache
  "Defines common functions and defs a cache for search collections.
  This namespace defines two separate caches that are tightly coupled and will be created and updated/refreshed at the same time:
  - coll-by-concept-id-cache = collection-for-gran-acls-by-concept-id-cache"
  (:require
   [clojure.walk :as walk]
   [cmr.elastic-utils.search.es-acl-parser :as acl-rhh]
   [cmr.elastic-utils.search.query-execution :as qe]
   [cmr.common.services.search.query-model :as q-mod]
   [cmr.common.date-time-parser :as time-parser]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [info]]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.util :as util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as red-hash-cache]))

(def coll-by-concept-id-cache-key
  "Identifies the key used when the cache is stored in the system."
  :collections-for-gran-acls-by-concept-id)

(def job-refresh-rate
  "This is the frequency that the cron job will run. It should be less than cache-ttl"
  ;; 15 minutes
  (* 60 15))

(def cache-ttl
  "This is when Redis should expire the data, this value should never be hit if
   the cron job is working correctly, however in some modes (such as local dev
   with no database) it may come into play."
   ;; 30 minutes
  (* 60 30))

(defn create-coll-by-concept-id-cache-client
  "Creates connection to collection-for-gran-acls coll-by-concept-id-cache.
  Field/Value Structure is as follows:
  concept-id -> {collection info}"
  []
  (red-hash-cache/create-redis-hash-cache {:keys-to-track [coll-by-concept-id-cache-key]
                                           :ttl           cache-ttl
                                           :read-connection (redis-config/redis-read-conn-opts)
                                           :primary-connection (redis-config/redis-conn-opts)}))

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
  "Take a map which has string dates and convert them to DateTime objects.
  This can be reversed with clj-times->time-strs."
  [data]
  (walk/postwalk
    #(if-let [valid-date (time-parser/try-parse-datetime %)] valid-date %)
    data))

(defn- execute-coll-for-gran-acls-query
  [context query-condition page-size]
  ;; when creating a result processor, realize all the lazy (delay) values to
  ;; actual values so that the resulting object can be cached in redis or some
  ;; other text based caching system and not clojure memory
  (let [result-processor (fn [_ _ elastic-item]
                           (assoc (util/delazy-all (acl-rhh/parse-elastic-item :collection elastic-item)) :concept-id (:_id elastic-item)))
        query (q-mod/query {:concept-type :collection
                            :condition       query-condition
                            :skip-acls?      true
                            :page-size       page-size
                            :result-format   :query-specified
                            :result-fields   (cons :concept-id acl-rhh/collection-elastic-fields)
                            :result-features {:query-specified {:result-processor result-processor}}})]
    (:items (qe/execute-query context query))))

(defn- fetch-collections
  "Fetches all collections or one collection by concept id from elastic. If no collections are found, will return nil."
  ([context]
   (execute-coll-for-gran-acls-query context q-mod/match-all :unlimited))
  ([context collection-concept-id]
   (let [query-condition (q-mod/string-condition :concept-id collection-concept-id true false)
         colls-found (execute-coll-for-gran-acls-query context query-condition 1)]
     (first colls-found))))

(defn refresh-entire-cache
  "Refreshes the collections-for-gran-acls coll-by-concept-id and coll-by-provider-id-and-entry-title caches.
  This should be called from a background job on a timer to keep the cache fresh.
  This will throw an exception if there is a problem fetching collections.
  The caller is responsible for catching and logging the exception."
  [context]
  (rl-util/log-refresh-start coll-by-concept-id-cache-key)
  (let [coll-by-concept-id-cache (hash-cache/context->cache context coll-by-concept-id-cache-key)
        collections (fetch-collections context)
        [tm _] (util/time-execution
                (doseq [coll collections]
                  (hash-cache/set-value coll-by-concept-id-cache
                                        coll-by-concept-id-cache-key
                                        (:concept-id coll)
                                        (clj-times->time-strs coll))))]
    (rl-util/log-redis-write-complete "refresh-entire-cache" coll-by-concept-id-cache-key tm)
    (info (str "Collections-for-gran-acls caches refresh complete."
         " coll-by-concept-id-cache Cache Size: " (hash-cache/cache-size coll-by-concept-id-cache coll-by-concept-id-cache-key) " bytes"))))

(defn set-cache
  "Updates collections-for-gran-acl cache for one given collection by concept id and returns found collection or nil"
  [context collection-concept-id]
   (let [collection-found (fetch-collections context collection-concept-id)
         coll-by-concept-id-cache (hash-cache/context->cache context coll-by-concept-id-cache-key)]
     (when collection-found
       (let [[tm _] (util/time-execution
                     (hash-cache/set-value coll-by-concept-id-cache
                                           coll-by-concept-id-cache-key
                                           (:concept-id collection-found)
                                           (clj-times->time-strs collection-found)))]
         (rl-util/log-redis-write-complete "set-caches" coll-by-concept-id-cache-key tm)))
     collection-found))

(defn get-collection-for-gran-acls
  "Gets a single collection from the cache by concept id. If collection is not found in cache, but exists in elastic, it will add it to the cache and will return the found collection."
  [context coll-concept-id]
  (let [coll-by-concept-id-cache (hash-cache/context->cache context coll-by-concept-id-cache-key)
        collection (hash-cache/get-value coll-by-concept-id-cache
                                         coll-by-concept-id-cache-key
                                         coll-concept-id)]
    (if (or (nil? collection) (empty? collection))
      (do
        (info (str "Collection with concept-id " coll-concept-id " not found in cache. Will update cache and try to find."))
        (time-strs->clj-times (set-cache context coll-concept-id)))
      (time-strs->clj-times collection))))

(defjob RefreshCollectionsCacheForGranuleAclsJob
        [_ctx system]
        (refresh-entire-cache {:system system}))

(defn refresh-collections-cache-for-granule-acls-job
  [job-key]
  {:job-type RefreshCollectionsCacheForGranuleAclsJob
   :job-key job-key
   :interval job-refresh-rate})
