(ns cmr.metadata-db.services.concept-service
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as cu]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.common.services.messages :as cmsg]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.services.concept-validations :as cv]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.metadata-db.data.providers :as provider-db]

            ;; Required to get code loaded
            [cmr.metadata-db.data.oracle.concepts]
            [cmr.metadata-db.data.oracle.concepts.collection]
            [cmr.metadata-db.data.oracle.concepts.granule]

            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]
            [clojure.set :as set]
            [clojure.string]))

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
        latest-revision (or previous-revision (c/get-concept db concept-type provider-id concept-id))
        expected-revision-id (inc (:revision-id latest-revision))]
    (if (= revision-id expected-revision-id)
      {:status :pass}
      {:status :fail
       :expected expected-revision-id})))

(defn validate-concept-revision-id
  "Validate that the revision-id for a concept (if given) is one greater than
  the current maximum revision-id for this concept."
  ([db concept previous-revision]
   (let [{:keys [concept-id revision-id]} concept]
     (cond
       (and revision-id concept-id)
       ;; both provided
       (let [result (check-concept-revision-id db concept previous-revision)]
         (when (= (:status result) :fail)
           (cmsg/data-error :conflict
                           msg/invalid-revision-id
                           (:expected result)
                           revision-id)))

       revision-id
       ;; only revision-id provided so it should be 1 (no concept-id has been assigned yet)
       (when-not (= revision-id 1)
         (cmsg/data-error :conflict
                         msg/invalid-revision-id
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
      (errors/internal-error! (msg/maximum-save-attempts-exceeded)))
    (condp = error-code
      :revision-id-conflict
      (when revision-id-provided?
        (cmsg/data-error :conflict
                        msg/invalid-revision-id-unknown-expected
                        revision-id-provided?))

      :concept-id-concept-conflict
      (let [{:keys [concept-id concept-type provider-id native-id]} concept]
        (cmsg/data-error :conflict
                        msg/concept-exists-with-differnt-id
                        concept-id
                        concept-type
                        provider-id
                        native-id))

      (errors/internal-error! (:error-message result)))))

(defn try-to-save
  "Try to save a concept by looping until we find a good revision-id or give up."
  [db concept revision-id-provided?]
  (loop [concept concept tries-left 3]
    (let [result (c/save-concept db concept)]
      (if (nil? (:error result))
        concept
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

(deftracefn get-concepts
  "Get multiple concepts by concept-id and revision-id. Returns concepts in order requested"
  [context concept-id-revision-id-tuples]
  (let [db (util/context->db context)
        ;; Split the tuples so they can be requested separately for each provider and concept type
        split-tuples-map (split-concept-id-revision-id-tuples concept-id-revision-id-tuples)]
    (validate-providers-exist db (keys split-tuples-map))
    (let [concepts (apply concat
                          (for [[provider-id concept-type-tuples-map] split-tuples-map
                                [concept-type tuples] concept-type-tuples-map]
                            ;; Retrieve the concepts for this type and provider id.
                            (c/get-concepts db concept-type provider-id tuples)))
          ;; Create a map of tuples to concepts
          concepts-by-tuple (into {} (for [c concepts] [[(:concept-id c) (:revision-id c)] c]))]
      (if (= (count concepts) (count concept-id-revision-id-tuples))
        ;; Return the concepts in the order they were requested
        (map concepts-by-tuple concept-id-revision-id-tuples)
        ;; some concepts weren't found
        (let [missing-concept-tuples (set/difference (set concept-id-revision-id-tuples)
                                                     (set (keys concepts-by-tuple)))]
          (errors/throw-service-errors
            :not-found
            (map (partial apply msg/concept-with-concept-id-and-rev-id-does-not-exist)
                 missing-concept-tuples)))))))

(deftracefn find-concepts
  "Find concepts with for a concept type with specific parameters"
  [context params]
  (cv/validate-find-params params)
  (let [db (util/context->db context)]
    (if (contains? (set (provider-db/get-providers db)) (:provider-id params))
      (c/find-concepts db params)
      ;; the provider doesn't exist
      [])))

(deftracefn save-concept
  "Store a concept record and return the revision."
  [context concept]
  (cv/validate-concept concept)
  (let [db (util/context->db context)]
    (validate-providers-exist db [(:provider-id concept)])
    (validate-concept-revision-id db concept nil)
    (let [revision-id-provided? (:revision-id concept)
          concept (->> concept
                       (set-or-generate-concept-id db)
                       (set-or-generate-revision-id db)
                       (set-deleted-flag false))]
      (try-to-save db concept revision-id-provided?))))

(deftracefn delete-concept
  "Add a tombstone record to mark a concept as deleted and return the revision-id of the tombstone."
  [context concept-id revision-id]
  (let [db (util/context->db context)
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        _ (validate-providers-exist db [provider-id])
        previous-revision (c/get-concept db concept-type provider-id concept-id)]
    (if previous-revision
      (if (util/is-tombstone? previous-revision)
        previous-revision
        (let [tombstone (merge previous-revision {:revision-id revision-id :deleted true :metadata ""})]
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

(defn delete-expired-concepts
  "Delete concepts that have not been deleted and have a delete-time before now"
  [db provider concept-type]
  (let [expired-concepts (c/get-expired-concepts db provider concept-type)]
    (when-not (empty? expired-concepts)
      (info "Deleting expired" (name concept-type) "concepts: " (map :concept-id expired-concepts)))
    (doseq [coll expired-concepts]
      (let [revision-id (inc (:revision-id coll))
            tombstone (merge coll {:revision-id revision-id :deleted true :metadata ""})]
        (try-to-save db tombstone revision-id)))))
