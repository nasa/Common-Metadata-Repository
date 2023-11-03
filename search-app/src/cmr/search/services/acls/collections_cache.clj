(ns cmr.search.services.acls.collections-cache
  "This is a cache of collection data for helping enforce granule acls in an efficient manner"
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.jobs :refer [defjob]]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.cache :as cache]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common-app.services.search.query-model :as q]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.redis-utils.redis-cache :as redis-cache]
            [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
            [cmr.common.util :as u]
            [clojure.pprint :as pp]
            [cmr.common.util :as util]))

;; No other file reads this cache
(def cache-key
  :collections-for-gran-acls)

(def initial-cache-state
  "The initial cache state."
  nil)

(defn create-cache
  "Creates a new empty collections cache."
  []
  ;;(mem-cache/create-in-memory-cache)
  (println "loading redis cache for collections")
  (redis-cache/create-redis-cache {:keys-to-track :collections-for-gran-acls :ttl (* 15 60)}))

(defn- fetch-collections
  "Executes a query that will fetch all of the collection information needed for caching."
  [context]
  (println "🚀🚀🚀 - here in fetch-collections")
  ;; when creating a result processor, relize all the lazy (delay) values to
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
    (println "🚀 - have collections" (first collections))
    {:by-concept-id by-concept-id
     :by-provider-id-entry-title by-provider-id-entry-title}))

(defn refresh-cache
  "Refreshes the collections stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching collections. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [cache (cache/context->cache context cache-key)
        collections-map (fetch-collections-map context)]
    ;;(println "serial:" (pr-str collections-map))
    ;;(print "round:" (clojure.edn/read-string (pr-str collections-map)))
    ;;(println "api:" (redis-cache/serialize collections-map))
    (cache/set-value cache cache-key collections-map)))

(defn- get-collections-map
  "Gets the cached value."
  [context]
  (let [coll-cache (cache/context->cache context cache-key)
        _ (println coll-cache)
        ;; the wrong cache is here, but still, why do I not have the values I want?
        collection-map (cache/get-value
                        coll-cache
                        cache-key
                        (fn [] (fetch-collections-map context)))]
    (println "🚀 - get-collections-map from cache resulted in" collection-map)
    (if (empty? collection-map)
      (errors/internal-error! "Collections were not in cache.")
      collection-map)))

(defn get-collection
  "Gets a single collection from the cache by concept id. Handles refreshing the cache if it is not found in it.
  Also allows provider-id and entry-title to be used."
  ([context concept-id]
   (println "🚀 - collections-cache/get-collection with" concept-id)
   (let [by-concept-id (:by-concept-id (get-collections-map context))]
     (println "🚀 - got collections map" by-concept-id)
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

(comment

  (get-collection cmr.search.services.acl-service/mycontext "C1200000026-TCHERRY.json")

  (let [system {:system (get-in user/system [:apps :search])}
        cache (cache/context->cache system cache-key)
        ;updated (refresh-cache system)
        obj (get-in cache [:by-concept-id "C1200000026-TCHERRY"])
        data (cache/get-value cache cache-key)]
    (println "obj:" obj)
    (clojure.pprint/pprint data)
    ;(println "updated:" updated)
    ;(println "AC - relized: " (cmr.common.util/get-real-or-lazy obj :AccessConstraints))
    ;(cmr.common.util/get-real-or-lazy obj :TemporalExtents)
    ;updated
    ;updated
    cache)


  (let [context {:system (get-in user/system [:apps :search])}
        cache (cache/context->cache context cache-key)
        collections-map (fetch-collections-map context)]
    (println "map:" collections-map)
    ;;(println "orig:" (keys (get-in collections-map [:by-concept-id "C1200000026-TCHERRY"])))
    ;;(println "serial:" (pr-str collections-map))
    ;;(print "round:" (clojure.edn/read-string (pr-str collections-map)))
    ;;(println "sapi:" (redis-cache/serialize collections-map))

    ;;(cache/set-value cache cache-key collections-map)
    (cache/get-value cache cache-key)
    cache)

  (let [ data {:concept-type :collection
               :provider-id "TCHERRY"
               :EntryTitle "LarcDatasetId-native1"
               :AccessConstraints {:Value nil}
               :TemporalExtents [{:RangeDateTimes [{:BeginningDateTime "2000-01-01T00:00:00.000Z", :EndingDateTime nil}]}]
               :concept-id "C1200000026-TCHERRY"}]
    (de-delay-collection data))

  )
