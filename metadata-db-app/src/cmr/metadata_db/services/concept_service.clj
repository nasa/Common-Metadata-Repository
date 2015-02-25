(ns cmr.metadata-db.services.concept-service
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as cu]
            [cmr.common.util :as cutil]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.config :as cfg]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.services.concept-validations :as cv]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.metadata-db.data.providers :as provider-db]
            [cmr.metadata-db.config :as config]

            ;; Required to get code loaded
            [cmr.metadata-db.data.oracle.concepts]
            [cmr.metadata-db.data.oracle.concepts.collection]
            [cmr.metadata-db.data.oracle.concepts.granule]
            [cmr.metadata-db.data.oracle.providers]

            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]
            [clojure.set :as set]
            [clojure.string]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as time-keeper]
            [cmr.metadata-db.services.concept-constraints :as cc]))


(def num-revisions-to-keep-per-concept-type
  "Number of revisions to keep by concept-type. If a concept instance has more than the number
  of revisions here the oldest ones will be deleted."
  {:collection 10
   :granule 1})

(def days-to-keep-tombstone
  "Number of days to keep a tombstone before is removed from the database."
  (cfg/config-value-fn :days-to-keep-tombstone 365 #(Integer. %)))

(def concept-truncation-batch-size
  "Maximum number of concepts to process in each iteration of the delete old concepts job."
  50000)

;;; utility methods

(defn validate-providers-exist
  "Validates that all of the providers in the list exist."
  [db provider-ids]
  (let [existing-provider-ids (set (provider-db/get-providers db))
        unknown-providers (set/difference (set provider-ids) existing-provider-ids)]
    (when (> (count unknown-providers) 0)
      (errors/throw-service-error :not-found (msg/providers-do-not-exist unknown-providers)))))

(defn- get-existing-concept-id
  "Retrieve concept-id from DB."
  [db concept]
  (validate-providers-exist db [(:provider-id concept)])
  (some-> (c/get-concept-by-provider-id-native-id-concept-type db concept)
          :concept-id))

(defn set-or-generate-concept-id
  "Get an existing concept-id from the DB for the given concept or generate one
  if the concept has never been saved."
  [db concept]
  (if (:concept-id concept)
    concept
    (let [concept-id (get-existing-concept-id db concept)]
      (if concept-id
        (assoc concept :concept-id concept-id)
        (assoc concept :concept-id (c/generate-concept-id db concept))))))

(defn set-or-generate-revision-id
  "Get the next available revision id from the DB for the given concept or
  zero if the concept has never been saved."
  [db concept & previous-revision]
  (if (:revision-id concept)
    concept
    (let [{:keys [concept-id concept-type provider-id]} concept
          previous-revision (first previous-revision)
          existing-revision-id (:revision-id (or previous-revision
                                                 (c/get-concept db concept-type provider-id concept-id)))
          revision-id (if existing-revision-id (inc existing-revision-id) 1)]
      (assoc concept :revision-id revision-id))))

(defn check-concept-revision-id
  "Checks that the revision-id for a concept is one greater than
  the current maximum revision-id for this concept."
  [db concept previous-revision]
  (let [{:keys [concept-id concept-type provider-id revision-id]} concept
        latest-revision (or previous-revision
                            (c/get-concept db concept-type provider-id concept-id)
                            ;; or it doesn't exist and the next should be 1
                            {:revision-id 0})
        expected-revision-id (inc (:revision-id latest-revision))]
    (if (= revision-id expected-revision-id)
      {:status :pass}
      {:status :fail
       :expected expected-revision-id})))

(defn validate-concept-revision-id
  "Validate that the revision-id for a concept (if given) is one greater than the current maximum
  revision-id for this concept. A third argument of the previous revision of the concept can be
  provided to avoid looking up the concept again."
  ([db concept]
   (validate-concept-revision-id db concept nil))
  ([db concept previous-revision]
   (let [{:keys [concept-id revision-id]} concept]
     (cond
       (and revision-id concept-id)
       ;; both provided
       (let [result (check-concept-revision-id db concept previous-revision)]
         (when (= (:status result) :fail)
           (cmsg/data-error :conflict
                            msg/invalid-revision-id
                            concept-id
                            (:expected result)
                            revision-id)))

       revision-id
       ;; only revision-id provided so it should be 1 (no concept-id has been assigned yet)
       (when-not (= revision-id 1)
         (cmsg/data-error :conflict
                          msg/invalid-revision-id
                          concept-id
                          1
                          revision-id))

       ;; just concept-id or neither provided - do nothing
       ))))

;;; this is abstracted here in case we switch to some other mechanism of
;;; marking tombstones
(defn- set-deleted-flag
  "Create a copy of the given and set its deleted flag to the given value.
  Used to create tombstones from concepts and vice-versa."
  [value concept]
  (assoc concept :deleted value))

(defn- handle-save-errors
  "Deal with errors encountered during saves."
  [concept result tries-left revision-id-provided?]
  (let [error-code (:error result)]
    (when (= tries-left 1)
      (errors/internal-error! (msg/maximum-save-attempts-exceeded (:error-message result))
                              (:throwable result)))
    (condp = error-code
      :revision-id-conflict
      (when revision-id-provided?
        (cmsg/data-error :conflict
                         msg/invalid-revision-id-unknown-expected
                         revision-id-provided?))

      :concept-id-concept-conflict
      (let [{:keys [concept-id concept-type provider-id native-id]} concept
            {:keys [existing-concept-id existing-native-id]} result]
        (cmsg/data-error :conflict
                         msg/concept-exists-with-different-id
                         existing-concept-id
                         existing-native-id
                         concept-id
                         native-id
                         concept-type
                         provider-id))

      (errors/internal-error! (:error-message result) (:throwable result)))))

(defn try-to-save
  "Try to save a concept by looping until we find a good revision-id or give up."
  [db concept revision-id-provided?]
  (loop [concept concept tries-left 3]
    (let [result (c/save-concept db concept)]
      (if (nil? (:error result))
        (do
          ;; Perform post commit constraint checks - don't perform check if deleting concepts
          (when-not (:deleted concept)
            ((cc/perform-post-commit-constraint-checks
               concept
               ;; When there are constraint violations we send in a rollback function to delete the
               ;; concept that had just been saved and then throw an error.
               #(c/force-delete db
                                (:concept-type concept)
                                (:provider-id concept)
                                (:concept-id concept)
                                (:revision-id concept)))
             db concept))
          concept)
        ;; depending on the error we will either throw an exception or try again (recur)
        (do
          (handle-save-errors concept result tries-left revision-id-provided?)
          (recur (set-or-generate-revision-id db concept nil) (dec tries-left)))))))

;;; service methods

(deftracefn get-concept
  "Get a concept by concept-id."
  ([context concept-id]
   (let [db (util/context->db context)
         {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)]
     (validate-providers-exist db [provider-id])
     (or (c/get-concept db concept-type provider-id concept-id)
         (cmsg/data-error :not-found
                          msg/concept-does-not-exist
                          concept-id))))
  ([context concept-id revision-id]
   (let [db (util/context->db context)
         {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)]
     (validate-providers-exist db [provider-id])
     (or (c/get-concept db concept-type provider-id concept-id revision-id)
         (cmsg/data-error :not-found
                          msg/concept-with-concept-id-and-rev-id-does-not-exist
                          concept-id
                          revision-id)))))

(defn split-concept-id-revision-id-tuples
  "Divides up concept-id-revision-id-tuples by provider and concept type."
  [concept-id-revision-id-tuples]
  (reduce (fn [m tuple]
            (let [{:keys [concept-type provider-id]} (cu/parse-concept-id (first tuple))]
              (update-in m [provider-id concept-type] #(if %
                                                         (conj % tuple)
                                                         [tuple]))))
          {}
          concept-id-revision-id-tuples))

(defn split-concept-ids
  "Divides up concept-ids by provider and concept type"
  [concept-ids]
  (reduce (fn [m concept-id]
            (let [{:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)]
              (update-in m [provider-id concept-type] #(conj % concept-id))))
          {}
          concept-ids))

(defn- filter-non-existent-providers
  "Removes providers that don't exist from a map of provider-ids to values."
  [db provider-id-map]
  (let [existing-provider-ids (set (provider-db/get-providers db))]
    (into {} (filter (comp existing-provider-ids first) provider-id-map))))

(deftracefn get-concepts
  "Get multiple concepts by concept-id and revision-id. Returns concepts in order requested"
  [context concept-id-revision-id-tuples allow-missing?]
  (info (format "Getting [%d] concepts by concept-id/revision-id"
                (count concept-id-revision-id-tuples)))
  (let [start (System/currentTimeMillis)
        parallel-chunk-size (get-in context [:system :parallel-chunk-size])
        db (util/context->db context)
        ;; Split the tuples so they can be requested separately for each provider and concept type
        split-tuples-map (split-concept-id-revision-id-tuples concept-id-revision-id-tuples)
        split-tuples-map (if allow-missing?
                           (filter-non-existent-providers db split-tuples-map)
                           (do
                             (validate-providers-exist db (keys split-tuples-map))
                             split-tuples-map))]

    (let [concepts (apply concat
                          (for [[provider-id concept-type-tuples-map] split-tuples-map
                                [concept-type tuples] concept-type-tuples-map]
                            ;; Retrieve the concepts for this type and provider id.
                            (if (and (> parallel-chunk-size 0) (< parallel-chunk-size (count tuples)))
                              ;; retrieving chunks in parallel for faster read performance
                              (apply concat
                                     (cutil/pmap-n-all
                                       (partial c/get-concepts db concept-type provider-id)
                                       parallel-chunk-size
                                       tuples))
                              (c/get-concepts db concept-type provider-id tuples))))
          ;; Create a map of tuples to concepts
          concepts-by-tuple (into {} (for [c concepts] [[(:concept-id c) (:revision-id c)] c]))]
      (if (or allow-missing? (= (count concepts) (count concept-id-revision-id-tuples)))
        ;; Return the concepts in the order they were requested
        (let [millis (- (System/currentTimeMillis) start)]
          (info (format "Found [%d] concepts in [%d] ms" (count concepts) millis))
          (keep concepts-by-tuple concept-id-revision-id-tuples))
        ;; some concepts weren't found
        (let [missing-concept-tuples (set/difference (set concept-id-revision-id-tuples)
                                                     (set (keys concepts-by-tuple)))]
          (errors/throw-service-errors
            :not-found
            (map (partial apply msg/concept-with-concept-id-and-rev-id-does-not-exist)
                 missing-concept-tuples)))))))

(deftracefn get-latest-concepts
  "Get the lastest version of concepts by specifiying a list of concept-ids. Results are
  returned in the order requested"
  [context concept-ids allow-missing?]
  (info (format "Getting [%d] latest concepts by concept-id" (count concept-ids)))
  (let [start (System/currentTimeMillis)
        parallel-chunk-size (get-in context [:system :parallel-chunk-size])
        db (util/context->db context)
        ;; Split the concept-ids so they can be requested separately for each provider and concept type
        split-concept-ids-map (split-concept-ids concept-ids)
        split-concept-ids-map (if allow-missing?
                                (filter-non-existent-providers db split-concept-ids-map)
                                (do
                                  (validate-providers-exist db (keys split-concept-ids-map))
                                  split-concept-ids-map))]
    (let [concepts (apply concat
                          (for [[provider-id concept-type-concept-id-map] split-concept-ids-map
                                [concept-type cids] concept-type-concept-id-map]
                            ;; Retrieve the concepts for this type and provider id.
                            (if (and (> parallel-chunk-size 0) (< parallel-chunk-size (count cids)))
                              ;; retrieving chunks in parallel for faster read performance
                              (apply concat
                                     (cutil/pmap-n-all
                                       (partial c/get-latest-concepts db concept-type provider-id)
                                       parallel-chunk-size
                                       cids))
                              (c/get-latest-concepts db concept-type provider-id cids))))
          ;; Create a map of concept-ids to concepts
          concepts-by-concept-id (into {} (for [c concepts] [(:concept-id c) c]))]
      (if (or allow-missing? (= (count concepts) (count concept-ids)))
        ;; Return the concepts in the order they were requested
        (let [millis (- (System/currentTimeMillis) start)]
          (info (format "Found [%d] concepts in [%d] ms" (count concepts) millis))
          (keep concepts-by-concept-id concept-ids))
        ;; some concepts weren't found
        (let [missing-concept-ids (set/difference (set concept-ids)
                                                  (set (keys concepts-by-concept-id)))]
          (errors/throw-service-errors
            :not-found
            (map msg/concept-does-not-exist
                 missing-concept-ids)))))))

(deftracefn find-concepts
  "Find concepts with for a concept type with specific parameters"
  [context params]
  (let [db (util/context->db context)
        latest-only? (= "true" (:latest params))
        params (dissoc params :latest)]
    (cv/validate-find-params params)
    (if (contains? (set (provider-db/get-providers db)) (:provider-id params))
      (if latest-only?
        (c/find-latest-concepts db params)
        (c/find-concepts db params))
      ;; the provider doesn't exist
      [])))

(deftracefn get-expired-collections-concept-ids
  "Returns the concept ids of expired collections in the provider."
  [context provider-id]
  (let [db (util/context->db context)]
    (validate-providers-exist db [provider-id])
    (distinct (map :concept-id (c/get-expired-concepts db provider-id :collection)))))

(deftracefn save-concept
  "Store a concept record and return the revision."
  [context concept]
  (cv/validate-concept concept)
  (let [db (util/context->db context)]
    (validate-providers-exist db [(:provider-id concept)])
    (validate-concept-revision-id db concept)
    (let [revision-id-provided? (:revision-id concept)
          concept (->> concept
                       (set-or-generate-concept-id db)
                       (set-or-generate-revision-id db)
                       (set-deleted-flag false))]
      (try-to-save db concept revision-id-provided?))))

(deftracefn delete-concept
  "Add a tombstone record to mark a concept as deleted and return the revision-id of the tombstone."
  [context concept-id revision-id revision-date]
  (let [db (util/context->db context)
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        _ (validate-providers-exist db [provider-id])
        previous-revision (c/get-concept db concept-type provider-id concept-id)]
    (if previous-revision
      (if (util/is-tombstone? previous-revision)
        previous-revision
        (let [tombstone (merge previous-revision {:revision-id revision-id :deleted true :metadata ""
                                                  :revision-date revision-date})]
          (cv/validate-concept tombstone)
          (validate-concept-revision-id db tombstone previous-revision)
          (let [revisioned-tombstone (set-or-generate-revision-id db tombstone previous-revision)]
            (try-to-save db revisioned-tombstone revision-id))))
      (if revision-id
        (cmsg/data-error :not-found
                         msg/concept-with-concept-id-and-rev-id-does-not-exist
                         concept-id
                         revision-id)
        ((cmsg/data-error :not-found
                          msg/concept-does-not-exist
                          concept-id))))))

(deftracefn force-delete
  "Remove a revision of a concept from the database completely."
  [context concept-id revision-id]
  (let [db (util/context->db context)
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        _ (validate-providers-exist db [provider-id])
        concept (c/get-concept db concept-type provider-id concept-id revision-id)]
    (if concept
      (c/force-delete db concept-type provider-id concept-id revision-id)
      (cmsg/data-error :not-found
                       msg/concept-with-concept-id-and-rev-id-does-not-exist
                       concept-id
                       revision-id))
    {:concept-id concept-id
     :revision-id revision-id}))

(deftracefn reset
  "Delete all concepts from the concept store and all providers."
  [context]
  (provider-service/reset-providers context)
  (c/reset (util/context->db context)))

(deftracefn get-concept-id
  "Get a concept id for a given concept."
  [context concept-type provider-id native-id]
  (cu/validate-concept-type concept-type)
  (let [db (util/context->db context)
        _ (validate-providers-exist db [provider-id])
        concept-id (c/get-concept-id db concept-type provider-id native-id)]
    (if concept-id
      concept-id
      (cmsg/data-error :not-found
                       msg/missing-concept-id
                       concept-type
                       provider-id
                       native-id))))

(deftracefn get-provider-holdings
  "Gets provider holdings within Metadata DB"
  [context]
  (let [db (util/context->db context)
        ;; Get a map of provider id to counts of granules per collection concept id
        provider-to-count-maps (into {} (pmap (fn [provider-id]
                                                [provider-id (c/get-concept-type-counts-by-collection
                                                               db :granule provider-id)])
                                              (provider-db/get-providers db)))
        ;; Flatten the map to a set of provider holdings except for entry title.
        holdings (for [[provider-id counts-map] provider-to-count-maps
                       [concept-id granule-count] counts-map]
                   {:provider-id provider-id
                    :concept-id concept-id
                    :granule-count granule-count})]
    ;; Populate entry title for each of the collections. This is slower than it has to be. We select
    ;; each entry title separately along with all of that other collection metadata. We can
    ;; speed this up if needed by adding a specific method to get a specific field of a bunch of
    ;; concepts all at once.
    (pmap (fn [{:keys [provider-id concept-id] :as holding}]
            (assoc holding :entry-title
                   (get-in (c/get-concept db :collection provider-id concept-id)
                           [:extra-fields :entry-title])))
          holdings)))

(defn delete-expired-concepts
  "Delete concepts that have not been deleted and have a delete-time before now"
  [context provider concept-type]
  (let [db (util/context->db context)]
    (loop []
      (let [expired-concepts (c/get-expired-concepts db provider concept-type)]
        (when (seq expired-concepts)
          (info "Deleting expired" (name concept-type) "concepts: " (map :concept-id expired-concepts))
          (doseq [coll expired-concepts]
            (let [revision-id (inc (:revision-id coll))
                  tombstone (merge coll {:revision-id revision-id :deleted true :metadata ""})]
              (try-to-save db tombstone revision-id)))
          (recur))))))

(defn force-delete-with
  "Continually force deletes concepts using the given function concept-id-revision-id-tuple-finder
  to find concept id revision id tuples to delete. Stops once the function returns an empty set."
  [db provider concept-type concept-id-revision-id-tuple-finder]
  (cutil/while-let
    [concept-id-revision-id-tuples (seq (concept-id-revision-id-tuple-finder))]
    (info "Deleting" (count concept-id-revision-id-tuples)
          "old concept revisions for provider" provider)
    (c/force-delete-concepts db provider concept-type concept-id-revision-id-tuples)))

(defn delete-old-revisions
  "Delete concepts to keep a fixed number of revisions around. It also deletes old tombstones that
  are older than a fixed number of days and any prior revisions of the deleted tombstone."
  [context provider concept-type]
  (let [db (util/context->db context)
        concept-type-name (str (name concept-type) "s")
        tombstone-cut-off-date (t/minus (time-keeper/now) (t/days (days-to-keep-tombstone)))]

    (info "Starting deletion of old" concept-type-name "for provider" provider)
    (force-delete-with
      db provider concept-type
      #(c/get-old-concept-revisions
         db
         provider
         concept-type
         (get num-revisions-to-keep-per-concept-type
              concept-type)
         concept-truncation-batch-size))

    (info "Starting deletion of tombstoned" concept-type-name "for provider" provider)
    (force-delete-with
      db provider concept-type
      #(c/get-tombstoned-concept-revisions
         db
         provider
         concept-type
         tombstone-cut-off-date
         concept-truncation-batch-size))))



