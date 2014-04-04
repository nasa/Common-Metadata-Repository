(ns cmr.metadata-db.services.concept-services
  "Sevices to support the business logic of the metadata db."
  (:require [cmr.metadata-db.data :as data]
            [cmr.common.services.errors :as errors]
            [cmr.metadata-db.services.messages :as messages]
            [cmr.metadata-db.services.utility :as util]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

;;; utility methods

(defn- context->db
  [context]
  (-> context :system :db))

(defn- get-existing-concept-id
  "Retrieve concept-id from DB."
  [db concept]
  (some-> (data/get-concept-by-provider-id-native-id-concept-type  db concept)
          :concept-id))

(defn- set-or-generate-concept-id 
  "Get an existing concept-id from the DB for the given concept or generate one 
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

(defn check-concept-revision-id
  "Checks that the revision-id for a concept is one greater than
  the current maximum revision-id for this concept."
  [db concept previous-revision]
  (let [{:keys [concept-id revision-id]} concept
        latest-revision (or previous-revision (data/get-concept db concept-id nil))
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
           (messages/data-error :conflict
                                messages/invalid-revision-id-msg
                                (:expected result)
                                revision-id)))
       
       revision-id
       ;; only revision-id provided so it should be zero (no concept-id has been assigned yet)
       (when-not (= revision-id 0)
         (messages/data-error :conflict
                              messages/invalid-revision-id-msg
                              0
                              revision-id))
       
       ;; just concept-id or neither provided - do nothing
       ))))

;;; this is abstracted here in case we switch to some other mechanism of
;;; marking tombstones
(defn- set-deleted-flag [value concept] (assoc concept :deleted value))

(defn- handle-save-errors 
  "Deal with errors encountered during saves."
  [concept result tries-left revision-id-provided?]
  (let [error-code (:error result)]
    (when (= tries-left 1)
      (errors/internal-error! (messages/maximum-save-attempts-exceeded-msg)))
    (cond 
      (= error-code :revision-id-conflict)
      (when revision-id-provided?
        (messages/data-error :conflict 
                             messages/invalid-revision-id-unknown-expected-msg
                             revision-id-provided?))
      
      (= error-code :concept-id-concept-conflict)
      (let [{:keys [concept-id concept-type provider-id native-id]} concept]
        (messages/data-error :conflict 
                             messages/concept-exists-with-differnt-id-msg
                             concept-id
                             concept-type
                             provider-id
                             native-id))
      
      :else
      (errors/internal-error! (:error-message result)))))

(defn try-to-save
  "Try to save a concept by looping until we find a good revision-id or give up."
  [db concept revision-id-provided?]
  (loop [concept concept tries-left 3]
    (let [result (data/save db concept)]
      (if (nil? (:error result))
        result
        ;; depending on the error we will either throw an exception or try again (recur)
        (do
          (handle-save-errors concept result tries-left revision-id-provided?)
          (recur (set-or-generate-revision-id db concept nil) (dec tries-left)))))))

;;; service methods

(deftracefn get-concept
  "Get a concept by concept-id."
  ([context concept-id]
   (if-let [concept (data/get-concept (context->db context) concept-id)]
     concept
     (messages/data-error :not-found
                          messages/concept-does-not-exist-msg
                          concept-id)))
  ([context concept-id revision-id]
   (if-let [concept (data/get-concept (context->db context) concept-id revision-id)]
     concept
     (messages/data-error :not-found
                          messages/concept-with-concept-id-and-rev-id-does-not-exist
                          concept-id
                          revision-id))))

(deftracefn get-concepts
  "Get multiple concepts by concept-id and revision-id."
  [context concept-id-revision-id-tuples]
  (vec (let [db (context->db context)]
         (data/get-concepts db concept-id-revision-id-tuples))))

(deftracefn save-concept
  "Store a concept record and return the revision."
  [context concept]
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
  (let [db (context->db context)
        previous-revision (data/get-concept db concept-id nil)]
    (if previous-revision
      (if (util/is-tombstone? previous-revision)
        previous-revision
        (let [tombstone (merge previous-revision {:revision-id revision-id :deleted true})]
          (validate-concept-revision-id db tombstone previous-revision)
          (let [revisioned-tombstone (set-or-generate-revision-id db tombstone previous-revision)]
            (try-to-save db revisioned-tombstone revision-id))))
      (if revision-id
        (messages/data-error :not-found
                             messages/concept-with-concept-id-and-rev-id-does-not-exist
                             concept-id
                             revision-id)
        ((messages/data-error :not-found
                              messages/concept-does-not-exist-msg
                              concept-id))))))

(deftracefn force-delete
  "Remove a revision of a concept from the database completely."
  [context concept-id revision-id]
  (let [db (context->db context)
        concept (data/get-concept db concept-id revision-id)]
    (if concept
      (data/force-delete db concept-id revision-id)
      (messages/data-error :not-found
                           messages/concept-with-concept-id-and-rev-id-does-not-exist
                           concept-id
                           revision-id))
    {:concept-id concept-id
     :revision-id revision-id}))


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
      (messages/data-error :not-found 
                           messages/missing-concept-id-msg
                           concept-type
                           provider-id
                           native-id))))
