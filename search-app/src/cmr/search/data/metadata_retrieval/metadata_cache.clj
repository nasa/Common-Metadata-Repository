(ns cmr.search.data.metadata-retrieval.metadata-cache
  "TODO

   TODO include this in individual doc functions
   ACLS are not applied by any fetching function"
  (require [cmr.common.cache :as c]
           [cmr.common.util :as u]
           [cmr.common.log :as log :refer (debug info warn error)]
           [cmr.search.services.result-format-helper :as rfh]
           [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
           [cmr.common-app.services.search.query-model :as q]
           [cmr.common-app.services.search.query-execution :as qe]
           [cmr.metadata-db.services.concept-service :as metadata-db]
           [cmr.umm-spec.core :as umm-spec]))

;; TODO add ability to get sizes in bytes of this cache
;; TODO test by downloading all the metadata as native from ops and then see how much memory caching
;; all of it actually takes



;; TODO add to system and then make sure to clear in reset
(def cache-key
  "TODO"
  :metadata-cache)

(def cached-formats
  "TODO
   could make this a config (though need to make it a set)
   cached umm-json is the latest version"
  #{:echo10
    ;; Note that when upgrading umm version we should also cache the previous version of UMM.
    {:format :umm-json
     :version "1.3"}})


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
  (atom {}))


(comment
 (def context {:system (assoc-in (get-in user/system [:apps :search])
                                 [:caches cache-key] (create-cache))})
 (refresh-cache context)
 (deref (c/context->cache context cache-key)))

(defn- fetch-collections
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

(defn- context->metadata-db-context
  "Converts the context into one that can be used to invoke the metadata-db services."
  [context]
  (assoc context :system (get-in context [:system :embedded-systems :metadata-db])))

;; TODO may not need this. It also assumes it's compressed
(defn- revision-format-map->concept
  "Converts a revision format map into a concept map using the target format. Assumes target format
   is present in revision format map."
  [target-format revision-format-map]
  {:pre [(get revision-format-map target-format)]}
  (let [{:keys [concept-id revision-id]} revision-format-map
        metadata (u/gzip-bytes->string (get revision-format-map target-format))]
    {:concept-id concept-id
     :revision-id revision-id
     :concept-type :collection
     :metadata metadata
     :format (rfh/search-result-format->mime-type target-format)}))

(defn- concept->revision-format-map
  "Converts a concept into a revision format map. See namespace documentation for details."
  ([context concept]
   (concept->revision-format-map context concept cached-formats))
  ([context concept target-format-set]
   (let [{:keys [concept-id revision-id metadata] concept-mime-type :format} concept
         native-format (rfh/mime-type->search-result-format concept-mime-type)
         base-map {:concept-id concept-id
                   :revision-id revision-id
                   :native-format native-format
                   native-format metadata}
         ;; Translate to all the cached formats except the native format.
         target-formats (disj target-format-set native-format)
         formats-map (metadata-transformer/transform-to-multiple-formats
                      context concept target-formats)]
     (merge base-map formats-map))))

(def non-gzipped-fields
  #{:concept-id :revision-id :native-format})

;; TODO unit test these

(defn gzip-revision-format-map
  "TODO"
  [revision-format-map]
  (into {} (mapv (fn [entry]
                   (let [k (key entry)]
                    (if (contains? non-gzipped-fields k)
                      entry
                      [k (u/string->gzip-bytes (val entry))])))
                 revision-format-map)))

(defn gzip-revision-format-map
  "TODO"
  [revision-format-map]
  (into {} (mapv (fn [entry]
                   (let [k (key entry)]
                    (if (contains? non-gzipped-fields k)
                      entry
                      [k (u/gzip-bytes->string (val entry))])))
                 revision-format-map)))

(defn refresh-cache
  "TODO"
  [context]
  (info "Refreshing metadata cache")
  (let [concepts-tuples (fetch-collections context)
        mdb-context (context->metadata-db-context context)
        [t1 concepts] (u/time-execution
                       (doall (metadata-db/get-concepts mdb-context concepts-tuples true)))
        ;; TODO consider using reducers here for better performance. Measure it.
        ;; Note this runs in a background process so it may not be advisable to tie up all cores for this.
        [t2 revision-format-maps] (u/time-execution
                                   (doall (pmap #(gzip-revision-format-map (concept->revision-format-map context %))
                                                concepts)))
        new-cache-value (reduce #(assoc %1 (:concept-id %2) %2) {} revision-format-maps)]
    (debug "Metadata cache refresh times: get-concepts time:" t1
           "concept->revision-format-map time:" t2)
    (reset! (c/context->cache context cache-key) new-cache-value)
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
  (let [cache (c/context->cache context cache-key)]
    (swap! cache #(reduce merge-revision-format-map % revision-format-maps))))

(defn fetch-and-cache-metadata
  "TODO
   use this if we want to provide easy caching"
  [context concept-tuples target-format allow-missing?]
  (let [mdb-context (context->metadata-db-context context)

        ;; Get Concepts from Metadata db
        [t1 concepts] (u/time-execution
                       (doall (metadata-db/get-concepts mdb-context concept-tuples allow-missing?)))

        ;; Convert concepts to revision format maps with the target format.
        target-format-set #{target-format}
        [t2 revision-format-maps] (u/time-execution
                                   (doall (pmap #(concept->revision-format-map context % target-format-set)
                                                concepts)))

        ;; Cache the revision format maps. Note time captured includes compression
        [t3 _] (u/time-execution
                (update-cache context (mapv gzip-revision-format-map revision-format-maps)))]

    (debug "fetch-and-cache of " (count concept-tuples) " concepts:"
           "get-concepts:" t1 "concept->revision-format-map:" t2 "update-cache:" t3)
    (into {} (mapv #(vector [(:concept-id %) (:revision-id %)] (get % target-format)) revision-format-maps))))

;; Not sure whether to use this one or previous
(defn fetch-metadata
  "TODO"
  [context concept-tuples target-format allow-missing?]
  (let [mdb-context (context->metadata-db-context context)
        ;; Get Concepts from Metadata db
        [t1 concepts] (u/time-execution
                       (doall (metadata-db/get-concepts mdb-context concept-tuples allow-missing?)))
        [t2 tuples-to-metadata] (u/time-execution
                                 (doall
                                  (pmap (fn [{:keys [concept-id revision-id] :as concept}]
                                          [[concept-id revision-id]
                                           (metadata-transformer/transform context concept target-format)])
                                        concepts)))]
    (debug "fetch of " (count concept-tuples) " concepts:"
           "get-concepts:" t1 "metadata-transformer/transform" t2)
    (into {} tuples-to-metadata)))

;; TODO unit test this
(defn- get-cached-metadata-in-format
  "Returns a sequecne of concept revision id tuples mapped to either the cached metadata in the
   requested format or a keyword representing why the cached metadata wasn't found."
  [context concept-tuples target-format]
  (let [cache-map (deref (c/context->cache context cache-key))]
    (reduce (fn [grouped-map tuple]
              (let [[concept-id revision-id] tuple
                    revision-format-map (get cache-map concept-id)]
                (if revision-format-map
                  ;; Concept is cached
                  (cond
                    ;; revision matches
                    (= revision-id (:revision-id revision-format-map))
                    (if-let [metadata (get revision-format-map target-format)]
                      (update grouped-map :metadata assoc tuple (u/gzip-bytes->string metadata))
                      (update grouped-map :target-format-not-cached conj tuple))

                    ;; Asking for a newer revision
                    (> revision-id (:revision-id revision-format-map))
                    (update grouped-map :newer-revision-requested conj tuple)

                    ;; Asking for an older revision
                    :else
                    (update grouped-map :older-revision-requested conj tuple))

                  ;; Concept not cached
                  (update grouped-map :concept-not-cached conj tuple))))
            {:metadata {}
             :target-format-not-cached []
             :concept-not-cached []
             :older-revision-requested []
             :newer-revision-requested []}
            concept-tuples)))


(defn get-formatted-concept-revisions
  "Get concepts with given concept-id, revision-id pairs in a given format."
  [context concept-tuples target-format allow-missing?]
  ;; TODO shouldn't we just skip all of this if we're doing granules? We could end up caching granules
  (let [results (get-cached-metadata-in-format context concept-tuples target-format)
        ;; Helper functions
        ;; TODO might be able to get rid of these if the tuples go at the end position.
        fetch #(fetch-metadata context % target-format allow-missing?)
        fetch-and-cache #(fetch-and-cache-metadata context % target-format allow-missing?)
        tuples-to-metadata (merge (:metadata results)
                                  (fetch-and-cache (concat (:concept-not-cached results)
                                                           (:newer-revision-requested results)
                                                           (:target-format-not-cached results)))
                                  (fetch (:older-revision-requested results)))]))



;; Note that when looking up concepts from metadata db we should do multiple at the same time.
;; Does core.async factor into this?


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

(defn get-latest-formatted-concepts
  "Get latest version of concepts with given concept-ids in a given format."
  [context concept-ids target-format])

;; lookup logic here should be similar to above with exceptions:
;; - We will not know if there's a newer revision than what we have cached.