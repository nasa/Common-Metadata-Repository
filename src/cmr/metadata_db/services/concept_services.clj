(ns cmr.metadata-db.services.concept-services
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.services.errors :as errors]
            [cmr.metadata-db.data.messages :as messages]
            [cmr.metadata-db.data.utility :as util]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

;;; utility methods

(defn- context->db
  [context]
  (-> context :system :db))

(defn- get-existing-concept-id
  "Retrieve concept-id from DB."
  [db concept]
  (some-> (data/get-concept-with-values db concept)
          :concept-id))

(defn- save-in-db
  "Saves the concept in database and returns the revision-id"
  [db concept]
  )

(defn- set-or-generate-concept-id 
  "Get an exiting concept-id from the DB for the given concept or generate one 
  if the concept has never been saved."
  [db concept]
  (if (:concept-id concept) 
    concept
    (let [concept-id (get-existing-concept-id db concept)]
      (if concept-id
        (assoc concept :concept-id concept-id)
        (assoc concept :concept-id (data/generate-concept-id db concept))))))

(defn- set-or-generate-revision-id
  "Get the next available revision id from the DB for the given concept or
  zero if the concept has never been saved."
  [db concept & previous-revision]
  (if (:revision-id concept)
    concept
    (let [previous-revision (first previous-revision)
          existing-revision-id (:revision-id (or previous-revision (data/get-concept db (:concept-id concept) nil)))
          revision-id (if existing-revision-id (inc existing-revision-id) 0)]
      (assoc concept :revision-id revision-id))))

;;; TODO move this to the services layer
(defn- validate-concept-revision-id
  "Validate that the revision-id for a concept (if given) is one greater than
  the current maximum revision-id for this concept."
  [db concept previous-revision]
  (let [{:keys [concept-id revision-id]} concept]
    (if revision-id
      (if concept-id
        (let [latest-revision (or previous-revision (data/get-concept db concept-id nil))
              expected-revision-id (inc (:revision-id latest-revision))]
          (when (not= revision-id expected-revision-id)
            (errors/throw-service-error :conflict
                                        (format messages/invalid-revision-id-msg
                                                expected-revision-id
                                                revision-id))))
        (if (not= revision-id 0)
          (errors/throw-service-error :conflict
                                      (format messages/invalid-revision-id-msg
                                              0
                                              revision-id)))))))



(defn- set-deleted-flag [value concept] (assoc concept :deleted value))


(defn- try-to-save
  "Try to save a concept by looping until we find a good revision-id or give up."
  [db concept revision-id-provided?]
  (loop [concept concept tries-left 3]
    (let [result (data/save db concept)]
      (if (nil? (:error result))
        result
        ;; depending on the error we will either throw an exception or try again (recur)
        (let [error-code (:error result)] 
          (cond 
            (= error-code :revision-id-conflict)
            (if revision-id-provided?
              (errors/throw-service-error :conflict (format messages/invalid-revision-id-unknown-expected-msg
                                                            revision-id-provided?))
              (if (= tries-left 1)
                (errors/internal-error! messages/maximum-save-attempts-exceeded-msg)))
            
            (= error-code :concept-id-concept-conflict)
            (let [{:keys [concept-id concept-type provider-id native-id]} concept]
              (errors/throw-service-error :conflict (format messages/concept-exists-with-differnt-id-msg
                                                            concept-id
                                                            concept-type
                                                            provider-id
                                                            native-id)))
            
            (= error-code :unknown-error)
            (errors/internal-error! "Unknown error saving concept")
            
            ; FIXME there might be a case where two first revision of a collection come in at the same time
            ;; they might accidentally get different concept ids. We'd need to recur then.
            )
          (recur (set-or-generate-revision-id db concept nil) (dec tries-left)))))))

;;; service methods

(deftracefn get-concept
  "Get a concept by concept-id."
  [context concept-id revision-id]
  (println (str "get context: " context))
  (if-let [concept (data/get-concept (context->db context) concept-id revision-id)]
    concept
    (if revision-id
      (errors/throw-service-error :not-found
                                  "Could not find concept with concept-id of %s and revision %s."
                                  concept-id
                                  revision-id)
      (errors/throw-service-error :not-found
                                  "Could not find concept with concept-id of %s."
                                  concept-id))))

(deftracefn get-concepts
  "Get multiple concepts by concept-id and revision-id."
  [context concept-id-revision-id-tuples]
  (vec (let [db (context->db context)]
         (data/get-concepts db concept-id-revision-id-tuples))))

(deftracefn save-concept
  "Store a concept record and return the revision."
  [context concept]
  (println (str "SAVE context: " context))
  (util/validate-concept concept)
  (let [db (context->db context)]
    (validate-concept-revision-id db concept nil)
    (let [concept-id-provided? (:concept-id concept)
          revision-id-provided? (:revision-id concept)
          concept (->> concept
                       (set-or-generate-concept-id db)
                       (set-or-generate-revision-id db)
                       (set-deleted-flag 0))]
      (try-to-save db concept revision-id-provided?))))

(deftracefn delete-concept
  "Add a tombstone record to mark a concept as deleted and return the revision-id of the tombstone."
  [context concept-id revision-id]
  (println (str "delete-concept " context))
  (let [db (context->db context)
        previous-revision (data/get-concept db concept-id nil)]
    (if previous-revision
      (if (util/is-tombstone? previous-revision)
        previous-revision
        (let [tombstone (merge previous-revision {:revision-id revision-id :deleted true})]
          (validate-concept-revision-id db tombstone previous-revision)
          (let [revisioned-tombstone (set-or-generate-revision-id db tombstone previous-revision)]
            (try-to-save db revisioned-tombstone revision-id))))
      (errors/throw-service-error :not-found
                                  (format messages/concept-does-not-exist-msg
                                          concept-id)))))

(deftracefn force-delete
  "Remove a revision of a concept from the database completely."
  [context concept-id revision-id])

(deftracefn reset
  "Delete all concepts from the concept store."
  [context]
  (data/reset (context->db context)))

(deftracefn get-concept-id
  "Get a concept id for a given concept."
  [context concept-type provider-id native-id]
  (let [concept-id (data/get-concept-id (context->db context) concept-type provider-id native-id)]
    (if concept-id
      (:concept_id concept-id)
      (errors/throw-service-error :not-found messages/missing-concept-id-msg
                                  concept-type
                                  provider-id
                                  native-id))))
