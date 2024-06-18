(ns cmr.search.data.metadata-retrieval.metadata-cache
  "Defines a cache for catalog item metadata. It currently only stores collections.

  The metadata cache contains data like the following:

  concept-id -> revision-format-map
                 * concept-id
                 * revision-id
                 * native-format - A key or map of format and version identifying the native format
                 * various format keys each mapped to compressed metadata."
  (:require
   [clj-time.core :as t]
   [clojure.set :as set]
   [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as cmn-coll-metadata-cache]
   [cmr.common-app.data.metadata-retrieval.revision-format-map :as crfm]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.memory-db.connection]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :as log :refer [debug info]]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as u]
   [cmr.metadata-db.services.concept-service :as metadata-db]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.umm-spec.acl-matchers :as acl-match]
   [cmr.umm-spec.versioning :as umm-version]))

(defconfig non-cached-collection-metadata-formats
  "Defines a set of collection metadata formats that will not be cached in memory"
  {:default #{}
   :type :edn})

(def all-formats
  "All the possible collection metadata formats that could be cached."
  #{:echo10
    :iso19115
    :dif
    :dif10
    ;; Note that when upgrading umm version we should also cache the previous version of UMM.
    {:format :umm-json
     :version umm-version/current-collection-version}})

(defn cached-formats
  "This is a set of formats that are cached."
  []
  (set/difference all-formats (non-cached-collection-metadata-formats)))

(defn cache-state
    "A helper function for debugging that returns a map of concept id to a map containing
     :revision-id and :cached-formats"
    [context]
    (let [cache (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)
          cache-map (-> (hash-cache/get-map cache cmn-coll-metadata-cache/cache-key)
                        (dissoc cmn-coll-metadata-cache/incremental-since-refresh-date-key 
                                cmn-coll-metadata-cache/collection-metadata-cache-fields-key))]
      (u/map-values (fn [rfm]
                      {:revision-id (:revision-id rfm)
                       :cached-formats (crfm/cached-formats rfm)})
                    cache-map)))

(defn- concept-tuples->cache-map
  "Takes a set of concept tuples fetches the concepts from metadata db, converts them to revision
   format maps, and stores them into a cache map"
  [context concept-tuples]
  (let [mdb-context (cmn-coll-metadata-cache/context->metadata-db-context context)
        concepts (doall (metadata-db/get-concepts mdb-context concept-tuples true))
        concepts (cmn-coll-metadata-cache/concepts-without-xml-processing-inst concepts)
        rfms (u/fast-map #(crfm/compress
                           (crfm/concept->revision-format-map context % (cached-formats) metadata-transformer/transform-to-multiple-formats true))
                         concepts)]
    (reduce #(assoc %1 (:concept-id %2) %2) {} rfms)))

(defn refresh-cache
  "Refreshes the collection metadata cache"
  [context]
  (info "Refreshing metadata cache")
  (let [incremental-since-refresh-date (str (clj-time.core/now))
        concepts-tuples (cmn-coll-metadata-cache/fetch-collections-from-elastic context)
        new-cache-value (reduce #(merge %1 (concept-tuples->cache-map context %2))
                                {}
                                (partition-all 1000 concepts-tuples))
        rcache (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)]
    (hash-cache/set-value rcache
                          cmn-coll-metadata-cache/cache-key
                          cmn-coll-metadata-cache/incremental-since-refresh-date-key
                          incremental-since-refresh-date)
    (hash-cache/set-value rcache
                          cmn-coll-metadata-cache/cache-key
                          cmn-coll-metadata-cache/collection-metadata-cache-fields-key
                          (vec (remove nil? (keys new-cache-value))))
    (hash-cache/set-values rcache cmn-coll-metadata-cache/cache-key new-cache-value)
    (info "Metadata cache refresh complete. Cache Size:"
          (hash-cache/cache-size rcache cmn-coll-metadata-cache/cache-key))))

(defn update-cache-job
  "Updates the collection metadata cache by querying elastic search for updates since the
  last time the cache was updated."
  [context]
    (info "Updating collection metadata cache")
    (let [cache (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)
          incremental-since-refresh-date (str (t/now))
          concepts-tuples (cmn-coll-metadata-cache/fetch-updated-collections-from-elastic context)
          new-cache-value (reduce #(merge %1 (concept-tuples->cache-map context %2))
                                  {}
                                  (partition-all 1000 concepts-tuples))
          new-concept-keys (keys new-cache-value)
          old-concept-keys (hash-cache/get-value cache
                                                 cmn-coll-metadata-cache/cache-key
                                                 cmn-coll-metadata-cache/collection-metadata-cache-fields-key)
          full-key-set (vec (distinct (remove nil? (concat new-concept-keys old-concept-keys))))]
      (hash-cache/set-value cache
                            cmn-coll-metadata-cache/cache-key
                            cmn-coll-metadata-cache/incremental-since-refresh-date-key
                            incremental-since-refresh-date)
      (hash-cache/set-value cache
                            cmn-coll-metadata-cache/cache-key
                            cmn-coll-metadata-cache/collection-metadata-cache-fields-key
                            full-key-set)
      (hash-cache/set-values cache
                             cmn-coll-metadata-cache/cache-key
                             new-cache-value)
      (info "Metadata cache update complete. Cache Size:" (hash-cache/cache-size cache cmn-coll-metadata-cache/cache-key))))

(defn in-memory-db?
  "Checks to see if the database in the context is an in-memory db."
  [system]
  (instance? cmr.common.memory_db.connection.MemoryStore
             (get-in {:system system} [:system :embedded-systems :metadata-db :db])))

(defjob RefreshCollectionsMetadataCache
  [ctx system]
  (when (in-memory-db? system)
    (refresh-cache {:system system})))

(defn refresh-collections-metadata-cache-job
  []
  {:job-type RefreshCollectionsMetadataCache
   ;; The time here is UTC.
   :daily-at-hour-and-minute [06 00]})

(defjob UpdateCollectionsMetadataCache
  [ctx system]
  (when (in-memory-db? system)
    (update-cache-job {:system system})))

(defn update-collections-metadata-cache-job
  []
  {:job-type UpdateCollectionsMetadataCache
   :interval (cmn-coll-metadata-cache/update-collection-metadata-cache-interval)})

(defn get-collection-metadata-cache-concept-ids
  "Returns a vector of collection concept ids that are stored in the collection-metadata-cache for the
  humanizer report."
  [context]
  (let [cache (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)]
    (sort
     (hash-cache/get-value cache
                           cmn-coll-metadata-cache/cache-key
                           cmn-coll-metadata-cache/collection-metadata-cache-fields-key))))

(defn get-concept-id
  "Given a concept-id, returns the value stored in the collection-metadata-cache for that concept which is
  documented at the top of this file."
  [context concept-id]
  (let [cache (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)]
    (hash-cache/get-value cache
                          cmn-coll-metadata-cache/cache-key
                          concept-id)))

(defn- update-cache
  "Updates the cache so that it will contain the updated revision format maps."
  [context revision-format-maps]
  (let [compressed-maps (u/fast-map crfm/compress revision-format-maps)
        rcache (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)
        [tm _] (u/time-execution
                (doall (map #(crfm/merge-into-cache-map rcache cmn-coll-metadata-cache/cache-key %) compressed-maps)))]
    (rl-util/log-redis-write-complete "update-cache" cmn-coll-metadata-cache/cache-key tm)

    (info "Cache updated with revision format maps. Cache Size:"
          (hash-cache/cache-size (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)
                                 cmn-coll-metadata-cache/cache-key))))

(defn- transform-and-cache
  "Takes existing revision format maps missing the target format, generates the format XML, and returns
   concepts with the requested format. Updates the cache with the generated XML."
  [context revision-format-maps target-format]
  (when (seq revision-format-maps)
    (let [[t1 updated-revision-format-maps] (u/time-execution
                                             (u/fast-map #(crfm/add-additional-format context target-format % metadata-transformer/transform)
                                                         revision-format-maps))
          [t2 concepts] (u/time-execution
                         (u/fast-map #(crfm/revision-format-map->concept target-format %)
                                     updated-revision-format-maps))
          ;; Cache the revision format maps.
          [t3 _] (u/time-execution
                  (when (contains? (cached-formats) target-format)
                    (update-cache context updated-revision-format-maps)))]
      (debug "transform-and-cache of " (count revision-format-maps) " concepts:"
             "add-additional-format:" t1 "revision-format-map->concept:" t2 "update-cache:" t3)
      concepts)))

(defn- fetch-and-cache-metadata
  "Fetches metadata from Metadata DB for the given concept tuples and converts them into the format
   requested. The original native format and the requested format are both stored in the cache."
  [context concept-tuples target-format]
  (when (seq concept-tuples)
    (let [mdb-context (cmn-coll-metadata-cache/context->metadata-db-context context)

          ;; Get Concepts from Metadata db
          [t1 concepts] (u/time-execution
                         (doall (metadata-db/get-concepts mdb-context concept-tuples false)))

          [t2 concepts] (u/time-execution
                         (cmn-coll-metadata-cache/concepts-without-xml-processing-inst concepts))

          ;; Convert concepts to revision format maps with the target format.
          target-format-set #{target-format}
          ;; revision-format-maps will contain nil if concept was deleted
          [t3 revision-format-maps] (u/time-execution
                                     (u/fast-map #(crfm/concept->revision-format-map context
                                                                                     %
                                                                                     target-format-set
                                                                                     metadata-transformer/transform-to-multiple-formats)
                                                 concepts))

          ;; Convert revision format maps to concepts with the specific format. We must return these
          ;; concepts because they contain the correct metadata.
          [t4 concepts] (u/time-execution
                         (mapv (fn [rvm concept]
                                 (if rvm
                                   (crfm/revision-format-map->concept target-format rvm)
                                   ;; rvm would be nil if concept was a tombstone. Return original tombstone in that case.
                                   concept))
                               revision-format-maps
                               concepts))

          ;; Cache the revision format maps. Note time captured includes compression
          [t5 _] (u/time-execution
                  (when (contains? (cached-formats) target-format)
                    (update-cache context (remove nil? revision-format-maps))))]

      (debug "fetch-and-cache of " (count concept-tuples) " concepts:"
             "get-concepts:" t1 "remove-xml-processing-instructions:" t2
             "concept->revision-format-map:" t3
             "revision-format-map->concept:" t4 "update-cache:" t5)
      concepts)))

(defn- fetch-metadata
  "Fetches metadata from Metadata DB for the given concept tuples and converts them into the format
   requested."
  [context concept-tuples target-format]
  (when (seq concept-tuples)
    (let [mdb-context (cmn-coll-metadata-cache/context->metadata-db-context context)
          ;; Get Concepts from Metadata db
          [t1 concepts] (u/time-execution
                         (doall (metadata-db/get-concepts mdb-context concept-tuples false)))
          [t2 concepts] (u/time-execution
                         (metadata-transformer/transform-concepts context concepts target-format))
          [t3 concepts] (u/time-execution
                         (cmn-coll-metadata-cache/concepts-without-xml-processing-inst concepts))]
      (info "fetch of " (count concept-tuples) " concepts:"
            "target-format:" target-format
            "get-concepts:" t1 "metadata-transformer/transform-concepts:" t2
            "concept-type: " (-> concepts first :concept-type)
            "remove-xml-processing-instructions:" t3)
      concepts)))

(defn- get-cached-metadata-in-format
  "Returns a a map of concepts found in the cache or other sets of items not found and mapped
   from a key that indicates why they weren't found in the cache."
  [context concept-tuples target-format]
  (let [cache (hash-cache/context->cache context cmn-coll-metadata-cache/cache-key)]
    (reduce (fn [grouped-map tuple]
              (let [[concept-id revision-id] tuple
                    [tm revision-format-map] (u/time-execution
                                              (hash-cache/get-value cache cmn-coll-metadata-cache/cache-key concept-id))]
                (rl-util/log-redis-read-complete "get-cached-metadata-in-format" cmn-coll-metadata-cache/cache-key tm)
                (if revision-format-map
                  ;; Concept is cached
                  (cond
                    ;; revision matches
                    (= revision-id (:revision-id revision-format-map))
                    (if (contains? revision-format-map target-format)
                      (update grouped-map :revision-format-maps conj revision-format-map)
                      (update grouped-map :target-format-not-cached conj revision-format-map))

                    ;; Asking for a newer revision
                    (> revision-id (:revision-id revision-format-map))
                    (update grouped-map :newer-revision-requested conj tuple)

                    ;; Asking for an older revision
                    :else
                    (update grouped-map :older-revision-requested conj tuple))

                  ;; Concept not cached
                  (update grouped-map :concept-not-cached conj tuple))))
            {;; contains revision format maps that needs to be decompressed
             :revision-format-maps []
             ;; Contains revision format maps waiting to be transformed
             :target-format-not-cached []
             ;; These will all contains concept tuples
             :concept-not-cached []
             :older-revision-requested []
             :newer-revision-requested []}
            concept-tuples)))

(defn- order-concepts
  "Puts concepts in order by concept tuples"
  [concept-tuples-order concepts]
  (let [by-concept-rev-id (group-by #(vector (:concept-id %) (:revision-id %)) concepts)]
    (mapv #(first (get by-concept-rev-id %)) concept-tuples-order)))

(defn get-formatted-concept-revisions
  "Returns concepts in the specific format requested. Uses cached metadata for collections."
  [context concept-type concept-tuples target-format]
  (if (= :collection concept-type)
    (let [;; Helper functions
          fetch #(fetch-metadata context % target-format)
          fetch-and-cache #(fetch-and-cache-metadata context % target-format)
          ;; Figure out which things are in the cache and which items are missing from the cache.
          [t1 results] (u/time-execution
                        (get-cached-metadata-in-format context concept-tuples target-format))
          ;; Convert items that were in the cache to concepts
          [t2 concepts1] (u/time-execution
                          (crfm/revision-format-maps->concepts target-format (:revision-format-maps results)))
          ;; Convert items that were in the cache but the format wasn't in the cache to concepts
          ;; and also cache the generated metadata
          [t3 concepts2] (u/time-execution
                          (transform-and-cache context (:target-format-not-cached results) target-format))
          ;; Fetch items that were missing from the cache and cache them.
          [t4 concepts3] (u/time-execution
                          (fetch-and-cache (concat (:concept-not-cached results)
                                                   (:newer-revision-requested results))))
          ;; Fetch the older revisions that were requested but don't cache those.
          [t5 concepts4] (u/time-execution
                          (fetch (:older-revision-requested results)))
          concepts (concat concepts1 concepts2 concepts3 concepts4)
          ;; Put everything in the order requested.
          [t6 ordered-concepts] (u/time-execution
                                 (order-concepts concept-tuples concepts))]
      (info "get-formatted-concept-revisions of" (count concept-tuples) "concepts total:"
             (+ t1 t2 t3 t4 t5 t6)
             "get-cached-metadata-in-format" t1
             "revision-format-maps->concepts:" t2
             "transform-and-cache:" t3
             "fetch-and-cache:" t4
             "fetch:" t5
             "order-concepts:" t6)
      ordered-concepts)

    ;; Concepts other than collection (e.g. granule, variable, service) won't use the cache
    (fetch-metadata context concept-tuples target-format)))

(defn get-latest-formatted-concepts
  "Get latest version of concepts with given concept-ids in a given format. Applies ACLs to the concepts
  found. If any of the concept ids are not found or were deleted then we just don't return them.
  Does not use the cache because this is currently only used when finding many granules by concept id
  or when finding a single concept."
  ([context concept-ids target-format]
   (get-latest-formatted-concepts context concept-ids target-format false))
  ([context concept-ids target-format skip-acls?]
   (info "Getting latest version of" (count concept-ids) "concept(s) in" target-format "format")

   (let [mdb-context (cmn-coll-metadata-cache/context->metadata-db-context context)
         [t1 concepts] (u/time-execution
                        ;; Allow missing is passed in as true here because this is implementing a
                        ;; search mechanism where some items just might not be found.
                        (doall (metadata-db/get-latest-concepts mdb-context concept-ids true)))
         ;; Filtering deleted concepts
         [t2 concepts] (u/time-execution (doall (remove :deleted concepts)))]
     (if skip-acls?
       ;; Convert concepts to results without acl enforcment
       (let [[t3 concepts] (u/time-execution
                            (metadata-transformer/transform-concepts
                             context concepts target-format))]
         (info "get-latest-concepts time:" t1
                "tombstone-filter time:" t2
                "metadata-transformer/transform-concepts time:" t3)
         concepts)

       ;; Convert concepts to results with acl enforcment
       (let [[t3 concepts] (u/time-execution (acl-match/add-acl-enforcement-fields context concepts))
             [t4 concepts] (u/time-execution (acl-service/filter-concepts context concepts))
             [t5 concepts] (u/time-execution
                            (metadata-transformer/transform-concepts
                             context concepts target-format))]
         (info "get-latest-concepts time:" t1
                "tombstone-filter time:" t2
                "add-acl-enforcement-fields time:" t3
                "acl-filter-concepts time:" t4
                "transform-concepts time:" t5)
         concepts)))))

(defn get-formatted-concept
  "Get a specific revision of a concept with the given concept-id in a given format.
  Applies ACLs to the concept found. Does not use the cache."
  [context concept-id revision-id target-format]
  (info "Getting revision" revision-id "of concept" concept-id "in" target-format "format")
  (let [mdb-context (cmn-coll-metadata-cache/context->metadata-db-context context)
        [t1 concept] (u/time-execution
                       (metadata-db/get-concept mdb-context concept-id revision-id))
        ;; Throw a service error for deleted concepts
        _ (when (:deleted concept)
            (errors/throw-service-errors
              :bad-request
              [(format
                 "The revision [%d] of concept [%s] represents a deleted concept and does not contain metadata."
                 revision-id
                 concept-id)]))
        [t2 concept] (u/time-execution (acl-match/add-acl-enforcement-fields-to-concept context concept))
        [t3 [concept]] (u/time-execution (acl-service/filter-concepts context [concept]))
        ;; format concept
        [t4 [concept]] (u/time-execution
                        (when concept
                         (metadata-transformer/transform-concepts context [concept] target-format)))]
    ;; We log the message below on INFO level as it is used by CMR log miner to replay the search
    (info "get-concept time:" t1
           "add-acl-enforcement-fields time:" t2
           "acl-filter-concepts time:" t3
           "metadata-transformer/transform-concepts time:" t4)
    concept))
