(ns cmr.indexer.data.collection-granule-aggregation-cache
  "Tracks various aggregations per collection across granules. The data is built from searching
   Elasticsearch. Collecting this data is relatively expensive so it is fetched and cached. The
   functions in this namespace can be used to fetch the information when indexing a collection.
   The data will be somewhat stale but should be adequate for the searching needs here."
  (require [cmr.common.jobs :refer [def-stateful-job]]
           [cmr.common.util :as util]
           [cmr.common.services.errors :as errors]
           ;; cache dependencies
           [cmr.common.cache :as c]
           [cmr.common.cache.fallback-cache :as fallback-cache]
           [cmr.common-app.cache.cubby-cache :as cubby-cache]
           [cmr.common-app.cache.consistent-cache :as consistent-cache]
           [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
           ;; elasticsearch dependencies
           [cmr.indexer.data.elasticsearch :as es]
           [clojurewerkz.elastisch.rest.document :as esd]
           [clojurewerkz.elastisch.query :as esq]
           [clj-time.core :as t]
           [clj-time.format :as f]
           [clj-time.coerce :as tc]))

(def coll-gran-aggregate-cache-key
  "The cache key to use when storing with caches in the system."
  :collection-granule-aggregation-cache)

(defn create-cache
  "Creates an instance of the cache."
  []
  ;; Single threaded lookup cache used to prevent indexing multiple items at the same time with
  ;; empty cache cause lots of lookups in elasticsearch.
  (stl-cache/create-single-thread-lookup-cache
    ;; Use the fall back cache so that the data is fast and available in memory
    ;; But if it's not available we'll fetch it from cubby.
    (fallback-cache/create-fallback-cache

      ;; Consistent cache is required so that if we have multiple instances of the indexer we'll have
      ;; only a single indexer refreshing it's cache.
      (consistent-cache/create-consistent-cache)
      (cubby-cache/create-cubby-cache))))

(def ^:private collection-aggregations
  "Defines the aggregations to use to find information about all the granules in a collection."
  {:collection-concept-id
   ;; Aggregate by collection concept id
   {:terms {:field :collection-concept-id
            ;; If we get more than 50K collections the parse-aggregations will detect that and throw
            ;; an exception.
            :size 50000}
    ;; Within that aggregation find these
    :aggs {;; Find the earliest occurence of a granule temporal
           :min-temporal {:min {:field :start-date-doc-values}}
           ;; Find the latest occurence of a granule temporal
           :max-temporal {:max {:field :end-date-doc-values}}
           ;; Determine if there are any granules with no end date.
           :no-end-date {:missing {:field :end-date-doc-values}}}}})

(defn- parse-numeric-date-value
  "Parses a numeric date value from Elasticsearch. The value returned usually seems to be a double
   containing the number of milliseconds since the epoch."
  [value]
  (when value
   (tc/from-long (long value))))

(defn- parse-aggregations
  "Parses the response back from Elasticsearch of collection aggregations. Returns a map of collection
   concept id to a map of information about that collection."
  [aggregate-response]
  (let [coll-concept-id-result (get-in aggregate-response [:aggregations :collection-concept-id])]
    (when (> (:sum_other_doc_count coll-concept-id-result) 0)
      (errors/internal-error!
       (str "Found more collections that expected when fetching collection concept ids: "
            (:sum_other_doc_count coll-concept-id-result))))
    (into
     {}
     (for [bucket (:buckets coll-concept-id-result)
           :let [concept-id (:key bucket)
                 earliest-start (parse-numeric-date-value
                                 (get-in bucket [:min-temporal :value]))
                 latest-end (parse-numeric-date-value
                             (get-in bucket [:max-temporal :value]))
                 some-with-no-end (> (get-in bucket [:no-end-date :doc_count]) 0)]]
       [concept-id
        {:granule-start-date earliest-start
         ;; Max end date will be nil if there are some that have no end date. This indicates they go on
         ;; forever.
         :granule-end-date (when-not some-with-no-end latest-end)}]))))

(defn- fetch-coll-gran-aggregates
  "Searches across all the granule indexes to aggregate by collection. Returns a map of collection
   concept id to collection information. The collection will only be in the map if it has granules."
  [context]
  (-> (esd/search (es/context->conn context)
                  "1_*" ;; Searching all indexes
                  ["granule"] ;; With the granule type.
                  {:query (esq/match-all)
                   :size 0
                   :aggs collection-aggregations})
      parse-aggregations))

(def ^:private date-time-format
  "The format Joda Time is written to when stored in the cache."
  (f/formatters :date-time))

(defn- joda-time->cachable-value
  "Takes a Joda time instance and converts it to a value that can be used with the cache. It must
   be convertable to and from EDN"
  [t]
  (when t
   (f/unparse date-time-format t)))

(defn- cached-value->joda-time
  "Parses a Joda time instance from a cached value."
  [v]
  (when v
   (f/parse date-time-format v)))

(defn- coll-gran-aggregates->cachable-value
  "Converts a collection granule aggregates map to a cachable-value value. It must be convertable to
   and from EDN"
  [coll-gran-aggregates]
  (util/map-values
   (fn [aggregate-map]
     (-> aggregate-map
         (update :granule-start-date joda-time->cachable-value)
         (update :granule-end-date joda-time->cachable-value)))
   coll-gran-aggregates))

(defn- cached-value->coll-gran-aggregates
  "Parses a cached value into a collection granule aggregates map."
  [cached-value]
  (util/map-values
   (fn [aggregate-map]
     (-> aggregate-map
         (update :granule-start-date cached-value->joda-time)
         (update :granule-end-date cached-value->joda-time)))
   cached-value))

(defn refresh-cache
  "Refreshes the collection granule aggregates in the cache."
  [context]
  (let [cache (c/context->cache context coll-gran-aggregate-cache-key)]
    (c/set-value cache coll-gran-aggregate-cache-key
                 (coll-gran-aggregates->cachable-value (fetch-coll-gran-aggregates context)))))

(defn get-coll-gran-aggregates
  "Returns the map of granule aggregate information for the collection. Will return nil if the
   collection has no granules."
  [context concept-id]
  (let [cache (c/context->cache context coll-gran-aggregate-cache-key)
        cached-value (c/get-value cache
                                  coll-gran-aggregate-cache-key
                                  (fn [] (coll-gran-aggregates->cachable-value
                                          (fetch-coll-gran-aggregates context))))
        cga-map (cached-value->coll-gran-aggregates cached-value)]
    (get cga-map concept-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Job for refreshing the cache. Only one node needs to refresh the cache because we're using the
;; fallback cache with cubby cache. The value stored in cubby will be available to all the nodes.

(def-stateful-job RefreshCollectionGranuleAggregateCacheJob
  [_ system]
  (refresh-cache {:system system}))

(def refresh-collection-granule-aggregate-cache-job
  "The singleton job that refreshes the cache."
  {:job-type RefreshCollectionGranuleAggregateCacheJob
   ;; Refresh once every hour
   :interval 3600})
