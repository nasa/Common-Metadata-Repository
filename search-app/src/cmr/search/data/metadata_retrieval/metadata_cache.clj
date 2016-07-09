(ns cmr.search.data.metadata-retrieval.metadata-cache
  "TODO

   TODO include this in individual doc functions
   ACLS are not applied by any fetching function"
  (require [cmr.common.util :as u]
           [cmr.common.cache :as c]
           [cmr.common.log :as log :refer (debug info warn error)]
           [cmr.search.services.result-format-helper :as rfh]
           [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
           [cmr.search.data.metadata-retrieval.revision-format-map :as rfm]
           [cmr.search.services.acl-service :as acl-service]
           [cmr.search.services.acls.acl-results-handler-helper :as acl-rhh]
           [cmr.common-app.services.search.query-model :as q]
           [cmr.common-app.services.search.query-execution :as qe]
           [cmr.metadata-db.services.concept-service :as metadata-db]
           [cmr.umm-spec.core :as umm-spec]))

;; TODO add ability to get sizes in bytes of this cache
;; TODO test by downloading all the metadata as native from ops and then see how much memory caching
;; all of it actually takes

(def cache-key
  "TODO"
  :metadata-cache)

(def cached-formats
  "TODO
   could make this a config (though need to make it a set)
   cached umm-json is the latest version"
  #{:echo10
    ;; TODO should we add other formats here?
    ;; Note that when upgrading umm version we should also cache the previous version of UMM.
    {:format :umm-json
     :version "1.3"}})

;; TODO comment this
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
  "TODO

   TODO document the map structure in namespace docs
   Map of concept id to revision format maps
   revision format maps have these keys
   * concept-id - the concept id of the cached collection
   * revision-id - The revision that is cached.
   * native-format - The key used in this map for storing the native metadata
   * :echo10 - the metadata converted to echo10 (or original if echo10 is native)
   * {:format :umm-json :version ...} - the metadata converted to umm-json in the specified revision
   * other format keys present depending on caching policies and always the native format

   native is cached so we can construct another non-cached format without having to go to metadata db.

   All metadata in the revision format maps is compressed byte arrays."
  []
  (->MetadataCache (atom {})))

(defn- fetch-collections-from-elastic
  "Executes a query that will fetch all of the collection information needed for caching."
  ;; TODO multiarity not necessary anymore.
  ([context]
   (fetch-collections-from-elastic context q/match-all true))
  ([context condition skip-acls?]
   (let [query (q/query {:concept-type :collection
                         :condition condition
                         :skip-acls? skip-acls?
                         :page-size :unlimited
                         :result-format :query-specified
                         :fields [:concept-id :revision-id]})]
     (mapv #(vector (:concept-id %) (:revision-id %))
           (:items (qe/execute-query context query))))))

(defn context->metadata-db-context
  "Converts the context into one that can be used to invoke the metadata-db services."
  [context]
  (assoc context :system (get-in context [:system :embedded-systems :metadata-db])))


(defn prettify-cache
  "TODO"
  [cache]
  (let [cache-map @(:cache-atom cache)]
    (u/map-values rfm/prettify cache-map)))


(comment
 (prettify-cache (get-in user/system [:apps :search :caches cache-key])))

;; TODO add a job for this
(defn refresh-cache
  "TODO"
  [context]
  (info "Refreshing metadata cache")
  (let [concepts-tuples (fetch-collections-from-elastic context)
        mdb-context (context->metadata-db-context context)
        [t1 concepts] (u/time-execution
                       (doall (metadata-db/get-concepts mdb-context concepts-tuples true)))
        ;; TODO consider using reducers here for better performance. Measure it.
        ;; Note this runs in a background process so it may not be advisable to tie up all cores for this.
        [t2 revision-format-maps] (u/time-execution
                                   (u/fast-map #(rfm/gzip-revision-format-map
                                                 (rfm/concept->revision-format-map context %))
                                             concepts))
        new-cache-value (reduce #(assoc %1 (:concept-id %2) %2) {} revision-format-maps)]
    (debug "Metadata cache refresh times: get-concepts time:" t1
           "concept->revision-format-map time:" t2)
    (reset! (:cache-atom (c/context->cache context cache-key)) new-cache-value)
    (info "Metadata cache refresh complete")
    nil))

;; TODO unit test this
(defn- merge-revision-format-map
  "Merges in the updated revision-format-map into the existing cache map.  The passed in revision
   format map can contain an unknown collection, a newer revision, or formats not yet cached. The
   data will be merged in the right way."
  [cache-map revision-format-map]
  (let [{:keys [concept-id revision-id]} revision-format-map]
    (if-let [curr-rev-format-map (cache-map concept-id)]
      ;; We've cached this concept
      (cond
        ;; We've got a newer revision
        (> revision-id (:revision-id curr-rev-format-map))
        (assoc cache-map concept-id revision-format-map)

        ;; We somehow retrieved older data than was cached. Keep newer data
        (< revision-id (:revision-id curr-rev-format-map))
        cache-map

        ;; Same revision
        :else
        ;; Merge in the newer data which may have additional cached formats.
        (assoc cache-map concept-id (merge curr-rev-format-map revision-format-map)))

      ;; We haven't cached this concept yet.
      (assoc cache-map concept-id revision-format-map))))

(defn- update-cache
  "Updates the cache so that it will contain the updated revision format maps."
  [context revision-format-maps]
  (let [cache (:cache-atom (c/context->cache context cache-key))]
    (swap! cache #(reduce merge-revision-format-map % revision-format-maps))))

(defn fetch-and-cache-metadata
  "TODO
   use this if we want to provide easy caching"
  [context concept-tuples target-format]
  (let [mdb-context (context->metadata-db-context context)

        ;; Get Concepts from Metadata db
        [t1 concepts] (u/time-execution
                       (doall (metadata-db/get-concepts mdb-context concept-tuples false)))

        ;; Convert concepts to revision format maps with the target format.
        target-format-set #{target-format}
        [t2 revision-format-maps] (u/time-execution
                                   (u/fast-map #(rfm/concept->revision-format-map context % target-format-set)
                                               concepts))

        ;; Cache the revision format maps. Note time captured includes compression
        [t3 _] (u/time-execution
                (update-cache context (u/fast-map rfm/gzip-revision-format-map revision-format-maps)))]

    (debug "fetch-and-cache of " (count concept-tuples) " concepts:"
           "get-concepts:" t1 "concept->revision-format-map:" t2 "update-cache:" t3)
    (mapv #(rfm/revision-format-map->concept target-format %) revision-format-maps)))

(defn- fetch-metadata
  "TODO"
  [context concept-tuples target-format]
  (let [mdb-context (context->metadata-db-context context)
        ;; Get Concepts from Metadata db
        [t1 concepts] (u/time-execution
                       (doall (metadata-db/get-concepts mdb-context concept-tuples false)))
        [t2 concepts] (u/time-execution
                       (metadata-transformer/transform-concepts context concepts target-format))]
    (debug "fetch of " (count concept-tuples) " concepts:"
           "get-concepts:" t1 "metadata-transformer/transform-concepts" t2)
    concepts))

;; TODO unit test this
(defn- get-cached-metadata-in-format
  "Returns a sequence of concept revision id tuples mapped to either the cached metadata in the
   requested format or a keyword representing why the cached metadata wasn't found."
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
                    (if-let [metadata (get revision-format-map target-format)]
                      (update grouped-map :metadata conj
                              (rfm/revision-format-map->concept target-format revision-format-map))
                      (update grouped-map :target-format-not-cached conj tuple))

                    ;; Asking for a newer revision
                    (> revision-id (:revision-id revision-format-map))
                    (update grouped-map :newer-revision-requested conj tuple)

                    ;; Asking for an older revision
                    :else
                    (update grouped-map :older-revision-requested conj tuple))

                  ;; Concept not cached
                  (update grouped-map :concept-not-cached conj tuple))))
            {:metadata []
             :target-format-not-cached []
             :concept-not-cached []
             :older-revision-requested []
             :newer-revision-requested []}
            concept-tuples)))

(defn concept-revision-id
  "Returns the concept revision id tuple for a concept"
  [concept]
  [(:concept-id concept) (:revision-id concept)])

(defn- order-concepts
  "Puts concepts in order by concept tuples"
  [concept-tuples-order concepts]
  (let [by-concept-rev-id (group-by concept-revision-id concepts)]
    (mapv #(first (get by-concept-rev-id %)) concept-tuples-order)))

(defn get-formatted-concept-revisions
  "Returns value maps with concept id, revision id, metadata and format."
  [context concept-type concept-tuples target-format]
  (if (= :collection concept-type)
    (let [results (get-cached-metadata-in-format context concept-tuples target-format)
          ;; Helper functions
          ;; TODO might be able to get rid of these if the tuples go at the end position.
          fetch #(fetch-metadata context % target-format)
          fetch-and-cache #(fetch-and-cache-metadata context % target-format)
          concepts (concat (:metadata results)
                           (fetch-and-cache (concat (:concept-not-cached results)
                                                    (:newer-revision-requested results)
                                                    (:target-format-not-cached results)))
                           (fetch (:older-revision-requested results)))]
      (order-concepts concept-tuples concepts))

    ;; Granule query. We don't cache those so just fetch from metadata db
    (fetch-metadata context concept-tuples target-format)))


;; TODO note that this isn't doing any caching and is really just a glorified faster search implementation.
;; It might make more sense for it to be implemented somewhere else. Or maybe the cache namespace here
;; should be split up. Note that we're probably going to have to implement acls with the get-formatted-concept-revisions
(defn get-latest-formatted-concepts
  "Get latest version of concepts with given concept-ids in a given format. Applies ACLs to the concepts
  found. If any of the concept ids are not found or were deleted then we just don't return them."
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


;; TODO throw this away
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; # Lookup logic
;; This should be unit testable without having to do any mocking. It should also be easy to tweak later.
;; Things skipping cache:
;; - granules
;; If in cache use that.
;; If cache miss
;; - specific revision is in cache but not format:
;;   - parse the native metadata and convert to that.
;;   - cache the requested format
;; - specific collection is in cache but newer revision:
;;   - Fetch revision from metadata db
;;   - convert to requested format.
;;   - cache requested format and native format. (others will be built as needed. The new revision
;;      will also be handled during a cache refresh.
;;   - Store the updated revision and remove the older revision.
;; - specific collection is in cache but older revision:
;;   - fetch revision from metadata db.
;;   - Convert to requested format
;;   - Do not cache this data (We don't want to cache every revision of collections because that
;;     could be a lot of metadata and I haven't checked sizing for this.



;; I think we can ignore all this. Get latest formatted conepts shouldn't be called for collections
;; We'll leave it in the transformer still working for collections but it won't be called that way.
; (defn get-latest-formatted-concepts
;   "Get latest version of concepts with given concept-ids in a given format."
;   [context concept-ids target-format skip-acls?]
;   (if (= :collection concept-type)
;     (let [condition (q/string-conditions :concept-id concept-ids)
;           concept-rev-tuples (fetch-collections-from-elastic
;                               context condition skip-acls?)])))
    ;; TODO granules
;
; ;; lookup logic here should be similar to above with exceptions:
; ;; - We will not know if there's a newer revision than what we have cached.


;; Current problem - how do we implement get-latest-formatted-concepts? Several options
;; granules direct fetch like in transformer
;; collections
;; - Get it from elasticsearch <-----
;; this seems like it's wrong to do here but
;; - Assume latest is in cache
;; -
;; -


;; ACLS are another monkey wrench
;; We could enforce acls during the search in elastic for collections.
;; For granules we implement basically exactly as it is right now.
