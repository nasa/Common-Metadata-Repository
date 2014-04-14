(ns cmr.metadata-db.services.concept-services
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.data.oracle.core :as oracle]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as cu]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.metadata-db.services.utility :as util]
            [cmr.metadata-db.services.provider-services :as provider-services]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

;;; utility methods

(defn- get-existing-concept-id
  "Retrieve concept-id from DB."
  [db concept]
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
          revision-id (if existing-revision-id (inc existing-revision-id) 0)]
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
           (msg/data-error :conflict
                           msg/invalid-revision-id-msg
                           (:expected result)
                           revision-id)))

       revision-id
       ;; only revision-id provided so it should be zero (no concept-id has been assigned yet)
       (when-not (= revision-id 0)
         (msg/data-error :conflict
                         msg/invalid-revision-id-msg
                         0
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
      (errors/internal-error! (msg/maximum-save-attempts-exceeded-msg)))
    (condp = error-code
      :revision-id-conflict
      (when revision-id-provided?
        (msg/data-error :conflict
                        msg/invalid-revision-id-unknown-expected-msg
                        revision-id-provided?))

      :concept-id-concept-conflict
      (let [{:keys [concept-id concept-type provider-id native-id]} concept]
        (msg/data-error :conflict
                        msg/concept-exists-with-differnt-id-msg
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
     (or (c/get-concept db concept-type provider-id concept-id)
         (msg/data-error :not-found
                         msg/concept-does-not-exist-msg
                         concept-id))))
  ([context concept-id revision-id]
   (let [db (util/context->db context)
         {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)]
     (or (c/get-concept db concept-type provider-id concept-id revision-id)
         (msg/data-error :not-found
                         msg/concept-with-concept-id-and-rev-id-does-not-exist
                         concept-id
                         revision-id)))))

(defn- split-concept-id-revision-id-tuples
  "Divides up concept-id-revision-id-tuples by provider and concept type."
  [concept-id-revision-id-tuples]
  )

(deftracefn get-concepts
  "Get multiple concepts by concept-id and revision-id."
  [context concept-id-revision-id-tuples]
  (vec (let [db (util/context->db context)]
    ;; TODO break apart by provider-id and concept-type
         #_(c/get-concepts db concept-id-revision-id-tuples))))

(deftracefn save-concept
  "Store a concept record and return the revision."
  [context concept]
  (util/validate-concept concept)
  (let [db (util/context->db context)]
    (validate-concept-revision-id db concept nil)
    (let [revision-id-provided? (:revision-id concept)
          concept (->> concept
                       (set-or-generate-concept-id db)
                       (set-or-generate-revision-id db)
                       (set-deleted-flag 0))]
      (try-to-save db concept revision-id-provided?))))

(deftracefn delete-concept
  "Add a tombstone record to mark a concept as deleted and return the revision-id of the tombstone."
  [context concept-id revision-id]
  (let [db (util/context->db context)
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        previous-revision (c/get-concept db concept-type provider-id concept-id nil)]
    (if previous-revision
      (if (util/is-tombstone? previous-revision)
        previous-revision
        (let [tombstone (merge previous-revision {:revision-id revision-id :deleted true})]
          (validate-concept-revision-id db tombstone previous-revision)
          (let [revisioned-tombstone (set-or-generate-revision-id db tombstone previous-revision)]
            (try-to-save db revisioned-tombstone revision-id))))
      (if revision-id
        (msg/data-error :not-found
                        msg/concept-with-concept-id-and-rev-id-does-not-exist
                        concept-id
                        revision-id)
        ((msg/data-error :not-found
                         msg/concept-does-not-exist-msg
                         concept-id))))))

(deftracefn force-delete
  "Remove a revision of a concept from the database completely."
  [context concept-id revision-id]
  (let [db (util/context->db context)
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        concept (c/get-concept db concept-type provider-id concept-id revision-id)]
    (if concept
      (c/force-delete db concept-type provider-id concept-id revision-id)
      (msg/data-error :not-found
                      msg/concept-with-concept-id-and-rev-id-does-not-exist
                      concept-id
                      revision-id))
    {:concept-id concept-id
     :revision-id revision-id}))

(deftracefn reset
  "Delete all concepts from the concept store and all providers."
  [context]
  (provider-services/reset-providers context)
  (c/reset (util/context->db context)))

(deftracefn get-concept-id
  "Get a concept id for a given concept."
  [context concept-type provider-id native-id]
  (let [db (util/context->db context)
        concept-id (c/get-concept-id db concept-type provider-id native-id)]
    (if concept-id
      (:concept_id concept-id)
      (msg/data-error :not-found
                      msg/missing-concept-id-msg
                      concept-type
                      provider-id
                      native-id))))
