(ns cmr.indexer.data.collection-granule-aggregation-cache
  "Tracks various aggregations per collection across granules. The data is built from searching
   Elasticsearch. Collecting this data is relatively expensive so it is fetched and cached. The
   functions in this namespace can be used to fetch the information when indexing a collection.
   The data will be somewhat stale but should be adequate for the searching needs here."
  (:require
   [clj-time.coerce :as tc]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojurewerkz.elastisch.query :as esq]
   [cmr.common-app.services.search.datetime-helper :as datetime-helper]
   [cmr.common.cache :as c]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [info]]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.services.index-service :as index-service]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.cache.consistent-cache :as consistent-cache]
   [cmr.transmit.metadata-db :as meta-db]))

(def coll-gran-aggregate-cache-key
  "The cache key to use when storing with caches in the system."
  :collection-granule-aggregation-cache)

(defconfig coll-gran-agg-cache-consistent-timeout-seconds
  "The number of seconds between when the collection granule aggregate cache should check with
   redis for consistence."
  {:default (* 5 60) ; 5 minutes
   :type Long})

(defn create-cache
  "Creates an instance of the cache."
  []
  ;; Single threaded lookup cache used to prevent indexing multiple items at the same time with
  ;; empty cache cause lots of lookups in elasticsearch.
  (stl-cache/create-single-thread-lookup-cache
    ;; Use the fall back cache so that the data is fast and available in memory
    ;; But if it's not available we'll fetch it from redis.
    (fallback-cache/create-fallback-cache

      ;; Consistent cache is required so that if we have multiple instances of the indexer we'll
      ;; have only a single indexer refreshing its cache.
      (consistent-cache/create-consistent-cache
       {:hash-timeout-seconds (coll-gran-agg-cache-consistent-timeout-seconds)})
      (redis-cache/create-redis-cache))))

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
  (-> (es-helper/search (es/context->conn context)
                        "1_small_collections,1_c*" ;; Searching all granule indexes
                        ["granule"] ;; With the granule type.
                        {:query (esq/match-all)
                         :size 0
                         :aggs collection-aggregations})
      parse-aggregations))

(defn- fetch-coll-gran-aggregates-updated-in-last-n
  "Searches across all the granule indexes to aggregate by collection for granules that were updated
   in the last N seconds. Returns a map of collection concept id to collection information. The
   collection will only be in the map if it has granules."
  [context granules-updated-in-last-n]
  (let [revision-date (t/minus (tk/now) (t/seconds granules-updated-in-last-n))
        revision-date-str (datetime-helper/utc-time->elastic-time revision-date)]
    (-> (es-helper/search (es/context->conn context)
                          "1_small_collections,1_c*" ;; Searching all granule indexes
                          ["granule"] ;; With the granule type.
                          {:query {:bool {:must (esq/match-all)
                                          :filter {:range {:revision-date-doc-values
                                                           {:gte revision-date-str}}}}}
                           :size 0
                           :aggs collection-aggregations})
        parse-aggregations)))

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

(defn- merge-granule-times
  "Merges the granule time maps returning a composite of times. Will only ever expand the ranges."
  [gt1 gt2]

  (if (and gt1 gt2)
    (let [{start1 :granule-start-date end1 :granule-end-date} gt1
          {start2 :granule-start-date end2 :granule-end-date} gt2]
      {:granule-start-date (if (< (compare start1 start2) 0) start1 start2)
       :granule-end-date (when (and end1 end2) ;; If either is nil return nil
                           ;; else return max time
                           (if (> (compare end1 end2) 0) end1 end2))})
    (or gt1 gt2)))

(defn- merge-coll-gran-aggregates
  "Merges the two collection granule aggregates returning an expanded time ranges."
  [cg1 cg2]
  (into {} (for [coll (distinct (concat (keys cg1) (keys cg2)))]
             [coll (merge-granule-times (get cg1 coll) (get cg2 coll))])))

(defn- full-cache-refresh
  "Fully refreshes the collection granule aggregate cache."
  [context]
  (let [cache (c/context->cache context coll-gran-aggregate-cache-key)]
    (info "Running a full refresh of the collection aggregation cache.")
    (c/set-value cache coll-gran-aggregate-cache-key
                 (coll-gran-aggregates->cachable-value (fetch-coll-gran-aggregates context)))))

(defn- collections-with-updated-times
  "Compares the existing aggregate map and an updated map and returns the collection concept ids which
   have different ranges."
  [existing-aggregate-map updated-map]
  (filter #(not= (existing-aggregate-map %) (updated-map %))
          (keys updated-map)))

(defn- partial-cache-refresh
  "Partially refreshes the collection granule aggregate cache. It looks for granules that have been
   updated or added to elasticsearch in the last N seconds. It will expand the existing time ranges
   in the collection granule aggregate cache. It does not handle collapsing time ranges due to
   granules being deleted or being updated to reduce their time range."
  [context granules-updated-in-last-n]
  (let [cache (c/context->cache context coll-gran-aggregate-cache-key)]
    (if-let [existing-value (c/get-value cache coll-gran-aggregate-cache-key)]

      (do
       (info "Running a partial refresh of the collection aggregation cache.")
       (let [existing-aggregate-map (cached-value->coll-gran-aggregates existing-value)
             recently-updated-granule-map (fetch-coll-gran-aggregates-updated-in-last-n
                                           context granules-updated-in-last-n)
             merged-map (merge-coll-gran-aggregates existing-aggregate-map
                                                    recently-updated-granule-map)
             updated-collections (collections-with-updated-times existing-aggregate-map merged-map)]
         (c/set-value cache coll-gran-aggregate-cache-key
                      (coll-gran-aggregates->cachable-value merged-map))

         (when (seq updated-collections)
          (info "Reindexing collections found with updated temporal values since last ingest:"
                (pr-str updated-collections))

          ;; Reindex the collections that were modified
          (->> updated-collections
               (meta-db/get-latest-concepts context)
               ;; wrap it in a vector to make a batch to bulk index
               vector
               (index-service/bulk-index context)))))

      ;; There's no existing value so a full refresh is required.
      (full-cache-refresh context))))

(defn refresh-cache
  "Refreshes the collection granule aggregates in the cache."
  [context granules-updated-in-last-n]
  (if granules-updated-in-last-n
    (partial-cache-refresh context granules-updated-in-last-n)
    (full-cache-refresh context)))

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
