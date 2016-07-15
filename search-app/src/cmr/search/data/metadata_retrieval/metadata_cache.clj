(ns cmr.search.data.metadata-retrieval.metadata-cache
  "Defines a cache for catalog item metadata. It currently only stores collections.

  The metadata cache contains data like the following:

  concept-id -> revision-format-map
                 * concept-id
                 * revision-id
                 * native-format - A key or map of format and version identifying the native format
                 * various format keys each mapped to compressed metadata."
  (require [cmr.common.util :as u]
           [cmr.common.cache :as c]
           [cmr.common.config :refer [defconfig]]
           [cmr.common.jobs :refer [defjob]]
           [cmr.common.services.errors :as errors]
           [cmr.common.log :as log :refer (debug info warn error)]
           [cmr.search.services.result-format-helper :as rfh]
           [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
           [cmr.search.data.metadata-retrieval.revision-format-map :as rfm]
           [cmr.search.services.acl-service :as acl-service]
           [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
           [cmr.umm-spec.versioning :as umm-version]
           [cmr.common-app.services.search.query-model :as q]
           [cmr.common-app.services.search.query-execution :as qe]
           [cmr.metadata-db.services.concept-service :as metadata-db]
           [cmr.umm-spec.core :as umm-spec]))

(def cache-key
  "Identifies the key used when the cache is stored in the system."
  :metadata-cache)

(def cached-formats
  "This is a set of formats that are cached."
  #{:echo10
    :iso19115
    :dif
    :dif10
    ;; Note that when upgrading umm version we should also cache the previous version of UMM.
    {:format :umm-json
     :version umm-version/current-version}})

;; Defines the record that represents the cache and implements the cache protocol. This is a very
;; simple implementation that just wraps an atom with the map of cached concept ids to revision
;; format maps.
(defrecord MetadataCache
  [cache-atom]
  c/CmrCache
  (get-keys
    [this]
    (keys @cache-atom))

  (get-value
    [this key]
    (get @cache-atom key))

  (get-value
    [this key lookup-fn]
    (throw (Exception. "Unsupported operation")))

  (reset
    [this]
    (reset! cache-atom {}))

  (set-value
    [this key value]
    (swap! cache-atom assoc key value)))

(defn create-cache
  "Creates an instance of the cache."
  []
  (->MetadataCache (atom {})))

(defn- fetch-collections-from-elastic
  "Executes a query that will fetch all of the collection information needed for caching."
  [context]
  (let [query (q/query {:concept-type :collection
                        :condition q/match-all
                        :skip-acls? true
                        :page-size :unlimited
                        :result-format :query-specified
                        :fields [:concept-id :revision-id]})]
    (mapv #(vector (:concept-id %) (:revision-id %))
          (:items (qe/execute-query context query)))))

(defn context->metadata-db-context
  "Converts the context into one that can be used to invoke the metadata-db services."
  [context]
  (assoc context :system (get-in context [:system :embedded-systems :metadata-db])))

(defn- prettify-cache
  "Returns a simplified version of the cache to help with debugging cache problems."
  [cache]
  (let [cache-map @(:cache-atom cache)]
    (u/map-values rfm/prettify cache-map)))

(defn- cache-size
  "Returns the combined size of all the metadata in the cache."
  [metadata-cache]
  (reduce + (map :size (vals @(:cache-atom metadata-cache)))))

(comment
 (refresh-cache {:system (get-in user/system [:apps :search])})
 (prettify-cache (get-in user/system [:apps :search :caches cache-key])))

(defn cache-state
  "A helper function for debugging that returns a map of concept id to a map containing
   :revision-id and :cached-formats"
  [context]
  (let [cache-map @(:cache-atom (c/context->cache context cache-key))]
    (u/map-values (fn [rfm]
                    {:revision-id (:revision-id rfm)
                     :cached-formats (rfm/cached-formats rfm)})
                  cache-map)))

(defn- concept-tuples->cache-map
  "Takes a set of concept tuples fetches the concepts from metadata db, converts them to revision
   format maps, and stores them into a cache map"
  [context concept-tuples]
  (let [mdb-context (context->metadata-db-context context)
        concepts (doall (metadata-db/get-concepts mdb-context concept-tuples true))
        rfms (u/fast-map #(rfm/compress
                           (rfm/concept->revision-format-map context % cached-formats))
                         concepts)]
    (reduce #(assoc %1 (:concept-id %2) %2) {} rfms)))

(defn refresh-cache
  "Refreshes the collection metadata cache"
  [context]
  (info "Refreshing metadata cache")
  (let [concepts-tuples (fetch-collections-from-elastic context)
        new-cache-value (reduce #(merge %1 (concept-tuples->cache-map context %2))
                                {}
                                (partition-all 1000 concepts-tuples))
        cache (c/context->cache context cache-key)]
    (reset! (:cache-atom cache) new-cache-value)
    (info "Metadata cache refresh complete. Cache Size:" (cache-size cache))
    nil))

(defconfig refresh-collection-metadata-cache-interval
  "The number of seconds between refreshes of the collection metadata cache"
  {:default (* 3600 8)
   :type Long})

(defjob RefreshCollectionsMetadataCache
  [ctx system]
  (refresh-cache {:system system}))

(defn refresh-collections-metadata-cache-job
  []
  {:job-type RefreshCollectionsMetadataCache
   :interval (refresh-collection-metadata-cache-interval)})

(defn- update-cache
  "Updates the cache so that it will contain the updated revision format maps."
  [context revision-format-maps]
  (let [cache (:cache-atom (c/context->cache context cache-key))
        compressed-maps (u/fast-map rfm/compress revision-format-maps)]
    (swap! cache #(reduce rfm/merge-into-cache-map % compressed-maps))
    (info "Cache updated with revision format maps. Cache Size:"
          (cache-size (c/context->cache context cache-key)))))

(defn- transform-and-cache
  "Takes existing revision format maps missing the target format, generates the format XML, and returns
   concepts with the requested format. Updates the cache with the generated XML."
  [context revision-format-maps target-format]
  (when (seq revision-format-maps)
   (let [[t1 updated-revision-format-maps] (u/time-execution
                                            (u/fast-map #(rfm/add-additional-format context target-format %)
                                                        revision-format-maps))
         [t2 concepts] (u/time-execution
                        (u/fast-map #(rfm/revision-format-map->concept target-format %)
                                    updated-revision-format-maps))
         ;; Cache the revision format maps.
         [t3 _] (u/time-execution (update-cache context updated-revision-format-maps))]
     (debug "transform-and-cache of " (count revision-format-maps) " concepts:"
            "add-additional-format:" t1 "revision-format-map->concept:" t2 "update-cache:" t3)
     concepts)))

(defn- fetch-and-cache-metadata
  "Fetches metadata from Metadata DB for the given concept tuples and converts them into the format
   requested. The original native format and the requested format are both stored in the cache."
  [context concept-tuples target-format]
  (when (seq concept-tuples)
    (let [mdb-context (context->metadata-db-context context)

          ;; Get Concepts from Metadata db
          [t1 concepts] (u/time-execution
                         (doall (metadata-db/get-concepts mdb-context concept-tuples false)))

          ;; Convert concepts to revision format maps with the target format.
          target-format-set #{target-format}
          [t2 revision-format-maps] (u/time-execution
                                     (u/fast-map #(rfm/concept->revision-format-map context % target-format-set)
                                                 concepts))

          ;; Convert revision format maps to concepts with the specific format. We must return these
          ;; concepts because they contain the correct metadata.
          [t3 concepts] (u/time-execution
                         (u/fast-map #(rfm/revision-format-map->concept target-format %)
                                     revision-format-maps))

          ;; Cache the revision format maps. Note time captured includes compression
          [t4 _] (u/time-execution (update-cache context revision-format-maps))]

      (debug "fetch-and-cache of " (count concept-tuples) " concepts:"
             "get-concepts:" t1 "concept->revision-format-map:" t2
             "revision-format-map->concept:" t3 "update-cache:" t4)
      concepts)))

(defn- fetch-metadata
  "Fetches metadata from Metadata DB for the given concept tuples and converts them into the format
   requested."
  [context concept-tuples target-format]
  (when (seq concept-tuples)
    (let [mdb-context (context->metadata-db-context context)
          ;; Get Concepts from Metadata db
          [t1 concepts] (u/time-execution
                         (doall (metadata-db/get-concepts mdb-context concept-tuples false)))
          [t2 concepts] (u/time-execution
                         (metadata-transformer/transform-concepts context concepts target-format))]
      (debug "fetch of " (count concept-tuples) " concepts:"
             "get-concepts:" t1 "metadata-transformer/transform-concepts" t2)
      concepts)))

(defn- get-cached-metadata-in-format
  "Returns a a map of concepts found in the cache or other sets of items not found and mapped
   from a key that indicates why they weren't found in the cache."
  [context concept-tuples target-format]
  (let [cache (deref (:cache-atom (c/context->cache context cache-key)))]
    (reduce (fn [grouped-map tuple]
              (let [[concept-id revision-id] tuple
                    revision-format-map (get cache concept-id)]
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
                          (rfm/revision-format-maps->concepts target-format (:revision-format-maps results)))
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
      (debug "get-formatted-concept-revisions of" (count concept-tuples) "concepts total:"
             (+ t1 t2 t3 t4 t5 t6)
             "get-cached-metadata-in-format" t1
             "revision-format-maps->concepts:" t2
             "transform-and-cache:" t3
             "fetch-and-cache:" t4
             "fetch:" t5
             "order-concepts:" t6)
      ordered-concepts)

    ;; Granule query. We don't cache those so just fetch from metadata db
    (fetch-metadata context concept-tuples target-format)))

(defn get-latest-formatted-concepts
  "Get latest version of concepts with given concept-ids in a given format. Applies ACLs to the concepts
  found. If any of the concept ids are not found or were deleted then we just don't return them.
  Does not use the cache because this is currently only used when finding granules by concept id."
  ([context concept-ids target-format]
   (get-latest-formatted-concepts context concept-ids target-format false))
  ([context concept-ids target-format skip-acls?]
   (info "Getting latest version of" (count concept-ids) "concept(s) in" target-format "format")

   (let [mdb-context (context->metadata-db-context context)
         [t1 concepts] (u/time-execution
                        ;; Allow missing is passed in as true here because this is implementing a
                        ;; search mechanism where some items just might not be found.
                        (doall (metadata-db/get-latest-concepts mdb-context concept-ids true)))
         ;; Filtering deleted concepts
         [t2 concepts] (u/time-execution (doall (filter #(not (:deleted %)) concepts)))]

     (if skip-acls?
       ;; Convert concepts to results without acl enforcment
       (let [[t3 concepts] (u/time-execution
                            (metadata-transformer/transform-concepts
                             context concepts target-format))]
         (debug "get-latest-concepts time:" t1
                "tombstone-filter time:" t2
                "transform-concepts time:" t3)
         concepts)

       ;; Convert concepts to results with acl enforcment
       (let [[t3 concepts] (u/time-execution (acl-rhh/add-acl-enforcement-fields concepts))
             [t4 concepts] (u/time-execution (acl-service/filter-concepts context concepts))
             [t5 concepts] (u/time-execution
                            (metadata-transformer/transform-concepts
                             context concepts target-format))]
         (debug "get-latest-concepts time:" t1
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
  (let [mdb-context (context->metadata-db-context context)
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
        [t2 concept] (u/time-execution (acl-rhh/add-acl-enforcement-fields-to-concept concept))
        [t3 [concept]] (u/time-execution (acl-service/filter-concepts context [concept]))
        ;; format concept
        [t4 [concept]] (u/time-execution
                        (when concept
                         (metadata-transformer/transform-concepts context [concept] target-format)))]
    (debug "get-concept time:" t1
           "add-acl-enforcement-fields time:" t2
           "acl-filter-concepts time:" t3
           "metadata-transformer/transform-concepts time:" t4)
    concept))

