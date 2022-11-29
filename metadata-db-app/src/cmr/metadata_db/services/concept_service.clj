(ns cmr.metadata-db.services.concept-service
  "Services to support the business logic of the metadata db."
  (:require
   [clj-time.core :as t]
   [clojure.set :as set]
   [cmr.common.concepts :as cu]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.date-time-parser :as p]
   [cmr.common.log :refer (debug error info warn trace)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.concepts :as c]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.data.providers :as provider-db]
   [cmr.metadata-db.services.concept-constraints :as cc]
   [cmr.metadata-db.services.concept-validations :as cv]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.provider-service :as provider-service]
   [cmr.metadata-db.services.search-service :as search]
   [cmr.metadata-db.services.util :as util])
  ;; Required to get code loaded
  ;; XXX This is really awful, and we do it a lot in the CMR. What we've got
  ;;     as a result of this is a leak from implementation into a separate part
  ;;     of the code ... not to mention that whatever is happing is is 100%
  ;;     implicit and chock-a-block with side effects. I believe the reason we
  ;;     do this is to work around issues with multimethods. We really need to
  ;;     refactor our multimethod code -- and this isn't the only reason
  ;;     (multimethods use slower code under the hood). If we really do need
  ;;     the flexible polymorphism that multimethods provide, then let's
  ;;     rethink our code reuse strategy around multimethods.
  (:require
   [cmr.metadata-db.data.oracle.concepts.acl]
   [cmr.metadata-db.data.oracle.concepts.collection]
   [cmr.metadata-db.data.oracle.concepts.generic-association]
   [cmr.metadata-db.data.oracle.concepts.generic-documents]
   [cmr.metadata-db.data.oracle.concepts.granule]
   [cmr.metadata-db.data.oracle.concepts.group]
   [cmr.metadata-db.data.oracle.concepts.humanizer]
   [cmr.metadata-db.data.oracle.concepts.subscription]
   [cmr.metadata-db.data.oracle.concepts.service-association]
   [cmr.metadata-db.data.oracle.concepts.service]
   [cmr.metadata-db.data.oracle.concepts.tag-association]
   [cmr.metadata-db.data.oracle.concepts.tag]
   [cmr.metadata-db.data.oracle.concepts.tool-association]
   [cmr.metadata-db.data.oracle.concepts.tool]
   [cmr.metadata-db.data.oracle.concepts.variable-association]
   [cmr.metadata-db.data.oracle.concepts.variable]
   [cmr.metadata-db.data.oracle.concepts]
   [cmr.metadata-db.data.oracle.providers]
   [cmr.metadata-db.data.oracle.search]))

(def num-revisions-to-keep-per-concept-type
  "Number of revisions to keep by concept-type. If a concept instance has more than the number
  of revisions here the oldest ones will be deleted."
  {:acl 10
   :collection 10
   :granule 1
   :tag 10
   :tag-association 10
   :access-group 10
   :humanizer 10
   :subscription 1
   :variable 10
   :variable-association 10
   :service 10
   :tool 10
   :service-association 10
   :tool-association 10
   :generic-association 10})

(defconfig days-to-keep-tombstone
  "Number of days to keep a tombstone before is removed from the database."
  {:default 365
   :type Long})

(def concept-truncation-batch-size
  "Maximum number of concepts to process in each iteration of the delete old concepts job."
  50000)

(def system-level-concept-types
  "A set of concept types that only exist on system level provider CMR."
  #{:tag
    :tag-association
    :humanizer
    :variable-association
    :service-association
    :tool-association
    :generic-association})

;;; utility methods
(def ^:private native-id-separator-character
  "This is the separator character used when creating the native id for an association."
  "/")

(def ^:private association-conflict-error-message
  "Failed to %s %s [%s] with collection [%s] because it conflicted with a concurrent %s on the
  same %s and collection. This means that someone is sending the same request to the CMR at the
  same time.")

(def ^:private association-unknown-error-message
  "Failed to %s %s [%s] with collection [%s] due to unknown type of error.")

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (cutil/lazy-get context :user-id)
    (errors/throw-service-error
     :unauthorized "Associations cannot be modified without a valid user token.")))

(defn- association->native-id
  "Returns the native id of the given association."
  [association]
  (let [{coll-concept-id :concept-id
         coll-revision-id :revision-id
         source-concept-id :source-concept-id} association
        native-id (str source-concept-id native-id-separator-character coll-concept-id)]
    (if coll-revision-id
      (str native-id native-id-separator-character coll-revision-id)
      native-id)))

(defn- association->concept-map
  [variable-association]
  (let [{:keys [source-concept-id originator-id native-id user-id data errors]
         coll-concept-id :concept-id
         coll-revision-id :revision-id} variable-association]
    {:concept-type :variable-association
     :native-id native-id
     :user-id user-id
     :format mt/edn
     :metadata (pr-str
                 (cutil/remove-nil-keys
                   {:variable-concept-id source-concept-id
                    :originator-id originator-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id
                    :data data}))
     :extra-fields {:variable-concept-id source-concept-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id}}))

(defn validate-system-level-concept
  "Validates that system level concepts are only associated with the system level provider,
  throws an error otherwise."
  [concept provider]
  (let [{concept-type :concept-type} concept
        {provider-id :provider-id} provider]
    (when (and (contains? system-level-concept-types concept-type)
               (not (:system-level? provider)))
      (let [err-msg (case concept-type
                      :tag (msg/tags-only-system-level provider-id)
                      :tag-association (msg/tag-associations-only-system-level provider-id)
                      :humanizer (msg/humanizers-only-system-level provider-id)
                      :variable-association (msg/variable-associations-only-system-level
                                             provider-id)
                      :service-association (msg/service-associations-only-system-level
                                             provider-id)
                      :tool-association (msg/tool-associations-only-system-level
                                          provider-id))]
        (errors/throw-service-errors :invalid-data [err-msg])))))

(defn- provider-ids-for-validation
  "Returns the set of provider-ids for validation purpose. It is a list of existing provider ids
  and 'CMR', which is reserved for tags / tag associations."
  [db]
  (set (conj (map :provider-id (provider-db/get-providers db)) "CMR")))

(defn- validate-providers-exist
  "Validates that all of the providers in the list exist."
  [db provider-ids]
  (let [existing-provider-ids (provider-ids-for-validation db)
        unknown-providers (set/difference (set provider-ids) existing-provider-ids)]
    (when (> (count unknown-providers) 0)
      (errors/throw-service-error :not-found (msg/providers-do-not-exist unknown-providers)))))

(defmulti set-or-generate-concept-id
  "Get an existing concept-id from the DB for the given concept or generate one if the concept
  has never been saved. Also validate the parent collection concept-id for granule concept."
  (fn [db provider concept]
    (:concept-type concept)))

(defmethod set-or-generate-concept-id :default
  [db provider concept]
  (if (:concept-id concept)
    concept
    (let [concept-id (c/get-concept-id db (:concept-type concept) provider (:native-id concept))
          concept-id (if concept-id concept-id (c/generate-concept-id db concept))]
      (assoc concept :concept-id concept-id))))

(defmethod set-or-generate-concept-id :granule
  [db provider concept]
  (if (:concept-id concept)
    concept
    (let [[existing-concept-id coll-concept-id deleted] (c/get-granule-concept-ids
                                                         db provider (:native-id concept))
          concept-id (if existing-concept-id existing-concept-id (c/generate-concept-id db concept))
          parent-concept-id (get-in concept [:extra-fields :parent-collection-id])]
      (if (and existing-concept-id
               (not= coll-concept-id parent-concept-id)
               (not deleted))
        (errors/throw-service-error
         :invalid-data (msg/granule-collection-cannot-change coll-concept-id parent-concept-id))
        (assoc concept :concept-id concept-id)))))

(defn- set-created-at-for-concept
  "Set the created-at of the given concept to the value of its previous revision if exists."
  [db provider concept]
  (let [{:keys [concept-id concept-type]} concept
        existing-created-at (:created-at
                             (c/get-concept db concept-type provider concept-id))]
    (if existing-created-at
      (assoc concept :created-at existing-created-at)
      concept)))

(defmulti set-created-at
  "Get the existing created-at value for the given concept and set it if it exists. Otherwise
  created-at will be set when saving to the Oracle database."
  (fn [db provider concept]
    (:concept-type concept)))

(defmethod set-created-at :collection
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :granule
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :service
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :tool
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :subscription
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(defmethod set-created-at :variable
  [db provider concept]
  (set-created-at-for-concept db provider concept))

(doseq [concept-type (cu/get-generic-concept-types-array)]
  (defmethod set-created-at concept-type
    [db provider concept]
    (set-created-at-for-concept db provider concept)))

(defmethod set-created-at :granule
  [db provider concept & previous-revision]
  (let [{:keys [concept-id concept-type]} concept
        previous-revision (first previous-revision)
        existing-created-at (:created-at (or previous-revision
                                             (c/get-concept db concept-type provider concept-id)))]
    (if existing-created-at
      (assoc concept :created-at existing-created-at)
      concept)))

(defmethod set-created-at :default
  [_db _provider concept]
  concept)

(defn- variable-missing-coll-info-msg
  "Returns the message for variable missing collection info"
  [native-id]
  (format "Variable [%s] can not be ingested without collection info."
          native-id))

(defn- set-or-generate-revision-id
  "Get the next available revision id from the DB for the given concept or
  one if the concept has never been saved."
  [db provider concept & previous-revision]
  (if (:revision-id concept)
    concept
    (let [{:keys [concept-id concept-type provider-id]} concept
          previous-revision (first previous-revision)
          existing-revision-id (:revision-id (or previous-revision
                                                 (c/get-concept db concept-type provider concept-id)))
          revision-id (if existing-revision-id (inc existing-revision-id) 1)]
      (assoc concept :revision-id revision-id))))

(defn- check-concept-revision-id
  "Checks that the revision-id for a concept is greater than
  the current maximum revision-id for this concept."
  [db provider concept previous-revision]
  (let [{:keys [concept-id concept-type revision-id]} concept
        latest-revision (or previous-revision
                            (c/get-concept db concept-type provider concept-id)
                            ;; or it doesn't exist and the next should be 1
                            {:revision-id 0})
        minimum-revision-id (inc (:revision-id latest-revision))]
    (if (>= revision-id minimum-revision-id)
      {:status :pass}
      {:status :fail
       :concept-id (:concept-id latest-revision)
       :expected minimum-revision-id})))

(defn- validate-concept-revision-id
  "Validate that the revision-id for a concept (if given) is greater than the current maximum
  revision-id for this concept. A third argument of the previous revision of the concept can be
  provided to avoid looking up the concept again."
  ([db provider concept]
   (validate-concept-revision-id db provider concept nil))
  ([db provider concept previous-revision]
   (when-let [revision-id (:revision-id concept)]
     (let [result (check-concept-revision-id db provider concept previous-revision)]
       (when (= (:status result) :fail)
         (cmsg/data-error :conflict
                          msg/invalid-revision-id
                          (:concept-id result)
                          (:expected result)
                          revision-id))))))

(defn- validate-new-variable-has-collection-info
  "Validate when variable is created, it contains collection info to make association with."
  [concept previous-concept]
  ;; When the variable doesn't have previous revision or the previous revision is tombstoned,
  ;; it indicates it's a new variale ingest. If it doesn't contain collection info to make association
  ;; with, the ingest should be rejected.
  (when (and (nil? (:coll-concept-id concept))
             (or (nil? previous-concept)
                 (util/is-tombstone? previous-concept)))
   (cmsg/data-error :invalid-data variable-missing-coll-info-msg (:native-id concept))))

;;; this is abstracted here in case we switch to some other mechanism of
;;; marking tombstones
(defn- set-deleted-flag
  "Create a copy of the given and set its deleted flag to the given value.
  Used to create tombstones from concepts and vice-versa."
  [value concept]
  (assoc concept :deleted value))

(defn- handle-save-errors
  "Deal with errors encountered during saves."
  [concept result]
  (case (:error result)
    :revision-id-conflict
    (cmsg/data-error :conflict
                     msg/concept-id-and-revision-id-conflict
                     (:concept-id concept)
                     (:revision-id concept))

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

    :variable-association-not-same-provider
    (cmsg/data-error :can-not-associate
                     str
                     (:error-message result))
    :collection-associated-with-variable-same-name
    (cmsg/data-error :can-not-associate
                     str
                     (:error-message result))

    :variable-associated-with-another-collection
    (cmsg/data-error :can-not-associate
                     str
                     (:error-message result))

    ;; else
    (errors/internal-error! (:error-message result) (:throwable result))))

(defmulti save-concept-revision
  "Store a concept record, which could be a tombstone, and return the revision."
  (fn [context concept]
    (boolean (:deleted concept))))

(defn- save-concept-in-mdb
  "Save the given concept in metadata-db using the given embedded metadata-db context."
  [mdb-context concept]
  (let [{:keys [concept-id revision-id]} (save-concept-revision mdb-context concept)]
    {:concept-id concept-id, :revision-id revision-id}))

(defn- delete-variable-association
  "Delete variable association with the given native-id using the given embedded metadata-db context."
  [mdb-context native-id]
  (let [existing-concept (first (search/find-concepts mdb-context
                                                      {:concept-type :variable-association
                                                       :native-id native-id
                                                       :exclude-metadata true
                                                       :latest true}))
        concept-id (:concept-id existing-concept)]
    (if concept-id
      (if (:deleted existing-concept)
        {:message {:warnings [(format "VARIABLE association [%s] is already deleted." concept-id)]}}
        (let [concept {:concept-type :variable-associationn
                       :concept-id concept-id
                       :user-id (context->user-id mdb-context)
                       :deleted true}]
          (save-concept-in-mdb mdb-context concept)))
      {:message {:warnings [(msg/delete-association-not-found
                             :variable native-id)]}})))

(defn- update-variable-association
  "Based on the input operation type (:insert or :delete), insert or delete the variable association
  using the embedded metadata-db, returns the association result in the format of
  {:variable-association {:concept-id VA1-CMR :revision-id 1}
   :associated-item {:concept_id C5-PROV1 :revision_id 2}}"
  [mdb-context association operation]
  ;; save each association if there is no errors on it, otherwise returns the errors.
  (let [{source-concept-id :source-concept-id
         coll-concept-id :concept-id
         coll-revision-id :revision-id} association
        native-id (association->native-id association)
        source-concept-type-str "variable"
        association (assoc association :native-id native-id)
        associated-item (cutil/remove-nil-keys
                         {:concept-id coll-concept-id :revision-id coll-revision-id})]
    (try
      (let [{:keys [concept-id revision-id message]} ;; delete-variable-association could potentially return message
            (if (= :insert operation)
              (save-concept-in-mdb
                mdb-context (association->concept-map association))
              (delete-variable-association mdb-context native-id))]
        (if (some? message)
          (merge {:associated-item associated-item} message)
          {:variable-association {:concept-id concept-id :revision-id revision-id}
           :associated-item associated-item}))
      (catch clojure.lang.ExceptionInfo e ; Report a specific error in the case of a conflict, otherwise throw error
        (let [exception-data (ex-data e)
              type (:type exception-data)
              errors (:errors exception-data)]
          (cond
            (= :conflict type)  {:errors (format association-conflict-error-message
                                                 (if (= :insert operation) "associate" "dissociate")
                                                 source-concept-type-str
                                                 source-concept-id
                                                 coll-concept-id
                                                 (if (= :insert operation) "association" "dissociation")
                                                 source-concept-type-str)
                                 :associated-item associated-item}
            (= :can-not-associate type) {:errors errors
                                         :associated-item associated-item}
            :else {:errors (format association-unknown-error-message
                                                 (if (= :insert operation) "associate" "dissociate")
                                                 source-concept-type-str
                                                 source-concept-id
                                                 coll-concept-id)}))))))

(defn- associate-variable
  "Associate variable concept to collection defined by coll-concept-id
  and coll-revision-id in concept."
  [context concept]
  (let [;; No more association validation here:
        ;; collection validation is moved to ingest.
        ;; Conflict validation is removed because we are replacing the old
        ;; association(s) with the new association.
        association (cutil/remove-nil-keys
                      {:concept-id (:coll-concept-id concept)
                       :revision-id (:coll-revision-id concept)
                       :source-concept-id (:concept-id concept)
                       :user-id (:user-id concept)
                       :data (:data concept)})]
    ;; context passed from perform-post-commit-association is already a mdb-context.
    (update-variable-association context association :insert)))

(defn get-concept
  "Get a concept by concept-id."
  ([context concept-id]
   (let [db (util/context->db context)
         {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
         provider (provider-service/get-provider-by-id context provider-id true)]
     (or (c/get-concept db concept-type provider concept-id)
         (cmsg/data-error :not-found
                          msg/concept-does-not-exist
                          concept-id))))
  ([context concept-id revision-id]
   (let [db (util/context->db context)
         {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
         provider (provider-service/get-provider-by-id context provider-id true)]
     (or (c/get-concept db concept-type provider concept-id revision-id)
         (cmsg/data-error :not-found
                          msg/concept-with-concept-id-and-rev-id-does-not-exist
                          concept-id
                          revision-id)))))

(defn- perform-post-commit-variable-association
  "Performs a post commit variable association. If failed, roll back
  variable ingest."
  [context concept rollback-function]
  ;; Before creating a variable association, make sure the collection
  ;; is not deleted to minimize the risk.
  (let [{:keys [coll-concept-id coll-revision-id]} concept
        collection (if coll-revision-id
                     (get-concept context coll-concept-id coll-revision-id)
                     (get-concept context coll-concept-id))
        err-msg (if coll-revision-id
                  (format "Collection [%s] revision [%s] is deleted from db"
                          coll-concept-id coll-revision-id)
                  (format "Collection [%s] is deleted from db"
                          coll-concept-id))]
    (when (:deleted collection)
      (rollback-function)
      (errors/throw-service-errors :invalid-data [err-msg])))

  (let [result (associate-variable context concept)]
    (when-let [errors (:errors result)]
      (rollback-function)
      (errors/throw-service-errors :conflict errors))
    result))

(defn- perform-post-commit-variable-dissociation
  "Performs a post commit variable dissociation - delete all the existing variable
  associations for the variable that va-concept is associated with. If failed, roll back
  ingest for va-concept - a variable association concept, throw error, which will roll
  back ingest for the variable."
  [context va-concept rollback-function]
  (let [variable-concept-id (get-in va-concept [:extra-fields :variable-concept-id])
        va-concept-id (:concept-id va-concept)
        vas (->> (search/find-concepts context
                                       {:concept-type :variable-association
                                        :variable-concept-id variable-concept-id
                                        :exclude-metadata true
                                        :latest true})
                  ;; Get all the associations except for the va-concept.
                  (filter #(not= va-concept-id (:concept-id %)))
                  (filter #(not (:deleted %))))]
    (doseq [va vas]
      (let [association {:source-concept-id (get-in va [:extra-fields :variable-concept-id])
                         :concept-id (get-in va [:extra-fields :associated-concept-id])
                         :revision-id (get-in va [:extra-fields :associated-revision-id])}
            result (update-variable-association context association :delete)
            err-msg (format "Can not dissociate existing association [%s]" (:concept-id va))]
        (when-let [errors (:errors result)]
          (rollback-function)
          ;; Error will be caught in update-variable-association when va-concept was inserted,
          ;; which will rollback variable ingest in perform-post-commit-variable-association.
          (errors/throw-service-errors :can-not-associate [err-msg]))))
    va-concept))

;; dynamic is here only for testing purposes to test failure cases.
(defn ^:dynamic try-to-save
  "Try to save a concept. The concept must include a revision-id. Ensures that revision-id and
  concept-id constraints are enforced as well as post commit uniqueness constraints. Returns the
  concept if successful, otherwise throws an exception."
  [db provider context concept]
  {:pre [(:revision-id concept)]}
  (let [result (c/save-concept db provider concept)
        ;; When there are constraint violations we send in a rollback function to delete the
        ;; concept that had just been saved and then throw an error.
        rollback-fn #(c/force-delete db
                                     (:concept-type concept)
                                     provider
                                     (:concept-id concept)
                                     (:revision-id concept))]
    (if (nil? (:error result))
      (do
        ;; Perform post commit constraint checks - don't perform check if deleting concepts
        (when-not (:deleted concept)
          (cc/perform-post-commit-constraint-checks
            db
            provider
            concept
            rollback-fn))

        ; Always perform a transaction-id post commit constraint check.
        (cc/perform-post-commit-transaction-id-constraint-check
          db
          provider
          concept
          rollback-fn)

        ;; Perform post commit variable association
        (if (and (= :variable (:concept-type concept))
                 (:coll-concept-id concept)
                 (not (:deleted concept)))
          ;; If errors occur, rollback, otherwise return variable concept with the association info:
          ;; {:variable_association {:concept_id VA1-CMR :revision_id 1},
          ;;  :associated_item {:concept_id C5-PROV1 :revision_id 2}}
          (let [assoc-info (perform-post-commit-variable-association context concept rollback-fn)]
            (merge concept assoc-info))
          (if (and (= :variable-association (:concept-type concept))
                   (not (:deleted concept)))
            ;; Perform post commit variable dissociation, if there exist any other variable associations,
            ;; for the same variable as the one in the variable association concept.
            ;; If errors occur, rollback the association insert, then throw error, which will rollback the
            ;; variable insert.
            (perform-post-commit-variable-dissociation context concept rollback-fn)
            concept)))
      (handle-save-errors concept result))))

;;; service methods

(defn- latest-revision?
  "Given a concept-id and a revision-id, perform a check whether the
  revision-id represents the most recent revision of the concept."
  [context concept-id revision-id]
  (= revision-id (:revision-id (get-concept context concept-id))))

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
  (let [existing-provider-ids (provider-ids-for-validation db)]
    (into {} (filter (comp existing-provider-ids first) provider-id-map))))

(defn get-concepts
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
                            (let [provider (provider-service/get-provider-by-id
                                            context provider-id true)]
                              ;; Retrieve the concepts for this type and provider id.
                              (if (and (> parallel-chunk-size 0)
                                       (< parallel-chunk-size (count tuples)))
                                ;; retrieving chunks in parallel for faster read performance
                                (apply concat
                                       (cutil/pmap-n-all
                                         (partial c/get-concepts db concept-type provider)
                                         parallel-chunk-size
                                         tuples))
                                (c/get-concepts db concept-type provider tuples)))))
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

(defn get-latest-concepts
  "Get the latest version of concepts by specifiying a list of concept-ids. Results are
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
                            (let [provider (provider-service/get-provider-by-id
                                            context provider-id true)]
                              ;; Retrieve the concepts for this type and provider id.
                              (if (and (> parallel-chunk-size 0)
                                       (< parallel-chunk-size (count cids)))
                                ;; retrieving chunks in parallel for faster read performance
                                (apply concat
                                       (cutil/pmap-n-all
                                         (partial c/get-latest-concepts db concept-type provider)
                                         parallel-chunk-size
                                         cids))
                                (c/get-latest-concepts db concept-type provider cids)))))
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

(defn get-expired-collections-concept-ids
  "Returns the concept ids of expired collections in the provider."
  [context provider-id]
  (let [db (util/context->db context)
        provider (provider-service/get-provider-by-id context provider-id true)]
    (distinct (map :concept-id (c/get-expired-concepts db provider :collection)))))

(defn- tombstone-associations
  "Tombstone the associations that matches the given search params, Also tombstone
  all the variables associated with these associations when concept-type is :collection,
  and assoc-type is :variable-association.
  skip-publication? flag controls if association deletion event should be generated,
  skip-publication? true means no association deletion event should be generated."
  [context assoc-type search-params skip-publication? concept-type]
  (let [associations (search/find-concepts context search-params)]
    (if (and (= :collection concept-type)
             (= :variable-association assoc-type))
      ;; When a collection is deleted, all the variables associated with it should be deleted,
      ;; which in turn will delete all the associations.
      (doseq [var-concept-id (distinct (map #(get-in % [:extra-fields :variable-concept-id])
                                            associations))]
        (save-concept-revision context {:concept-type :variable
                                        :concept-id var-concept-id
                                        :deleted true
                                        :user-id "cmr"
                                        :skip-publication false}))
      (doseq [association associations]
        (save-concept-revision context {:concept-type assoc-type
                                        :concept-id (:concept-id association)
                                        :deleted true
                                        :user-id "cmr"
                                        :skip-publication skip-publication?})))))

(defn- delete-associations-for-collection-concept
  "Delete the associations associated with the given collection revision and association type,
  no association deletion event is generated."
  [context assoc-type coll-concept-id coll-revision-id]
  (let [search-params1 (cutil/remove-nil-keys
                        {:concept-type assoc-type
                         :source-concept-identifier coll-concept-id
                         :source-revision-id coll-revision-id
                         :exclude-metadata true
                         :latest true})
        search-params2 (cutil/remove-nil-keys
                        {:concept-type assoc-type
                         :associated-concept-id coll-concept-id
                         :associated-revision-id coll-revision-id
                         :exclude-metadata true
                         :latest true})]
    (when (= :generic-association assoc-type)
      ;;For generic associations, concept collection could appear as both source-concept
      ;;and associated-concept so it needs to tombstone additional associations.
      (tombstone-associations context assoc-type search-params1 true :collection))
    (tombstone-associations context assoc-type search-params2 true :collection)))

(defmulti delete-associations
  "Delete the associations of the given association type that is associated with
  the given concept type and concept id.
  assoc-type can be :variable-association, :service-association or :tool-association,
  concept-type can be :collection, :variable, :service or :tool."
  (fn [context concept-type concept-id revision-id assoc-type]
    concept-type))

(defmethod delete-associations :default
  [context concept-type concept-id revision-id assoc-type]
  ;; does nothing by default
  nil)

(defmethod delete-associations :collection
  [context concept-type concept-id revision-id assoc-type]
  ;; only delete the associated variable associations
  ;; if the given revision-id is the latest revision of the collection
  (let [[latest-coll] (search/find-concepts context {:concept-type :collection
                                                     :concept-id concept-id
                                                     :exclude-metadata true
                                                     :latest true})]
    (when (or (nil? revision-id)
              (= revision-id (:revision-id latest-coll)))
      (delete-associations-for-collection-concept context assoc-type concept-id nil))))

(defmethod delete-associations :variable
  [context concept-type concept-id revision-id assoc-type]
  (when (= :variable-association assoc-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type assoc-type
                          :variable-concept-id concept-id
                          :exclude-metadata true
                          :latest true})]
      ;; create variable association tombstones and queue the variable association delete events
      (tombstone-associations context assoc-type search-params false :variable)))
  (when (= :generic-association assoc-type)
    (let [search-params1 (cutil/remove-nil-keys
                          {:concept-type assoc-type
                           :source-concept-identifier concept-id
                           :source-revision-id revision-id
                           :exclude-metadata true
                           :latest true})
          search-params2 (cutil/remove-nil-keys
                          {:concept-type assoc-type
                           :associated-concept-id concept-id
                           :associated-revision-id revision-id
                           :exclude-metadata true
                           :latest true})]
      (tombstone-associations context assoc-type search-params1 false :variable)
      (tombstone-associations context assoc-type search-params2 false :variable))))

(defmethod delete-associations :service
  [context concept-type concept-id revision-id assoc-type]
  (when (= :service-association assoc-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type assoc-type
                          :service-concept-id concept-id
                          :exclude-metadata true
                          :latest true})]
      ;; create service association tombstones and queue the service association delete events
      (tombstone-associations context assoc-type search-params false :service)))
  (when (= :generic-association assoc-type)
    (let [search-params1 (cutil/remove-nil-keys
                          {:concept-type assoc-type
                           :source-concept-identifier concept-id
                           :source-revision-id revision-id
                           :exclude-metadata true
                           :latest true})
          search-params2 (cutil/remove-nil-keys
                          {:concept-type assoc-type
                           :associated-concept-id concept-id
                           :associated-revision-id revision-id
                           :exclude-metadata true
                           :latest true})]
      (tombstone-associations context assoc-type search-params1 false :service)
      (tombstone-associations context assoc-type search-params2 false :service))))

(defmethod delete-associations :tool
  [context concept-type concept-id revision-id assoc-type]
  (when (= :tool-association assoc-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type assoc-type
                          :tool-concept-id concept-id
                          :exclude-metadata true
                          :latest true})]
      ;; create tool association tombstones and queue the tool association delete events
      (tombstone-associations context assoc-type search-params false :tool)))
   (when (= :generic-association assoc-type)
    (let [search-params1 (cutil/remove-nil-keys
                          {:concept-type assoc-type
                           :source-concept-identifier concept-id
                           :source-revision-id revision-id
                           :exclude-metadata true
                           :latest true})
          search-params2 (cutil/remove-nil-keys
                          {:concept-type assoc-type
                           :associated-concept-id concept-id
                           :associated-revision-id revision-id
                           :exclude-metadata true
                           :latest true})]
      (tombstone-associations context assoc-type search-params1 false :tool)
      (tombstone-associations context assoc-type search-params2 false :tool))))

(doseq [concept-type (cu/get-generic-concept-types-array)]
  (defmethod delete-associations concept-type
    [context concept-type concept-id revision-id assoc-type]
    (when (= :generic-association assoc-type)
      (let [search-params1 (cutil/remove-nil-keys
                            {:concept-type assoc-type
                             :source-concept-identifier concept-id
                             :source-revision-id revision-id
                             :exclude-metadata true
                             :latest true})
            search-params2 (cutil/remove-nil-keys
                            {:concept-type assoc-type
                             :associated-concept-id concept-id
                             :associated-revision-id revision-id
                             :exclude-metadata true
                             :latest true})]
        ;;For generic associations, conceptn could appear as both source-concept
        ;;and associated-concept so it needs to tombstone additional associations.
      (tombstone-associations context assoc-type search-params1 false concept-type)
      (tombstone-associations context assoc-type search-params2 false concept-type)))))

;; true implies creation of tombstone for the revision
(defmethod save-concept-revision true
  [context concept]
  (cv/validate-tombstone-request concept)
  (let [{:keys [concept-id revision-id revision-date user-id skip-publication]} concept
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        db (util/context->db context)
        provider (provider-service/get-provider-by-id context provider-id true)
        _ (validate-system-level-concept concept provider)
        previous-revision (c/get-concept db concept-type provider concept-id)]
    (if previous-revision
      ;; For a concept which is already deleted (i.e. previous revision is a tombstone),
      ;; new revision is created only if the revision id is supplied. We don't want extraneous
      ;; tombstones created which, for example, can happen if multiple delete requests are sent at
      ;; once for the same concept. But if a revision id is sent we need to validate it and store a
      ;; record in the database. The revision id allows a client (including virutal product service)
      ;; to send concept updates and deletions out of order.
      (if (and (util/is-tombstone? previous-revision) (nil? revision-id))
        previous-revision
        (let [metadata (if (cu/generic-concept? concept-type)
                         (:metadata previous-revision)
                         "")
              tombstone (merge previous-revision {:concept-id concept-id
                                                  :revision-id revision-id
                                                  :revision-date revision-date
                                                  :user-id user-id
                                                  :metadata metadata
                                                  :deleted true})]
          (cv/validate-concept tombstone)
          (validate-concept-revision-id db provider tombstone previous-revision)
          (let [revisioned-tombstone (->> (set-or-generate-revision-id db provider tombstone previous-revision)
                                          (try-to-save db provider context))]
            ;; delete the associated variable associations if applicable
            (delete-associations context concept-type concept-id nil :variable-association)
            ;; delete the associated service associations if applicable
            (delete-associations context concept-type concept-id nil :service-association)
            ;; delete the associated tool associations if applicable
            (delete-associations context concept-type concept-id nil :tool-association)
            ;; delete the associated generic associations if applicable
            (delete-associations context concept-type concept-id nil :generic-association)

            ;; Missing from this list is tag association, retain these records in case the
            ;; concept is brought back to life (un-tombstoned). This is a feature not a bug
            ;; backed up by tests in tag_association_test.clj

            ;; skip publication flag is set for tag association when its associated
            ;; collection revision is force deleted. In this case, the association is no longer
            ;; needed to be indexed, so we don't publish the deletion event.
            ;; We can't let the message get published because by the time indexer get the message,
            ;; the associated collection revision is gone and indexer won't be able to find it.
            ;; The association is potentially created by a different user than the provider,
            ;; so a collection revision is force deleted doesn't necessarily mean that the
            ;; association is no longer needed. People might want to see what is in the old
            ;; association potentially and force deleting it seems to run against the rationale
            ;; that we introduced revisions in the first place.
            ;; skip publication flag is also set for variable association when its associated
            ;; collection revision is deleted. Indexer will index the variables associated with
            ;; the collection through the collection delete event. Not the variable association
            ;; delete event.

            ;; CMR-8712: After deleting a collection, searching for services, tools, and generic
            ;; documents that are associated with the collection, still show the collection association.
            ;; This is because skip-publication is set to true for all associations when a collection is
            ;; deleted. Given the above comments regarding the need for the tag and variable associations
            ;; to skip the delete event publication, service/tool/generic association delete events should
            ;; still be published.
            (when (or (not skip-publication)
                      (= concept-type :generic-association)
                      (= concept-type :service-association)
                      (= concept-type :tool-association))
              (ingest-events/publish-event
               context (ingest-events/concept-delete-event revisioned-tombstone)))
            revisioned-tombstone)))
      (if revision-id
        (cmsg/data-error :not-found
                         msg/concept-with-concept-id-and-rev-id-does-not-exist
                         concept-id
                         revision-id)
        (cmsg/data-error :not-found
                         msg/concept-does-not-exist
                         concept-id)))))

(defn- publish-service-associations-update-event
  "Publish one concept-update-event for all non-tombstoned service associations for the
  given service concept; This is to trigger the reindexing of the associated collections in elastic
  search when service is updated because service info is indexed into the associated collections.
  Does nothing if the given concept is not a service concept."
  [context concept-type concept-id]
  (when (= :service concept-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type :service-association
                          :service-concept-id concept-id
                          :exclude-metadata true
                          :latest true})
          associations (filter #(= false (:deleted %))
                               (search/find-concepts context search-params))]
      (when (> (count associations) 0)
        (ingest-events/publish-event
         context
         (ingest-events/associations-update-event associations))))))

(defn- publish-generic-associations-update-event
  "Publish one concept-update-event for all non-tombstoned generic associations for the
  given generic concept. This is to trigger the reindexing of the related generic concepts in elastic
  search when associated generic concept is updated."
  [context concept-type concept-id]
  (let [search-params1 (cutil/remove-nil-keys
                       {:concept-type :generic-association
                        :source-concept-identifier concept-id
                        :exclude-metadata true
                        :latest true})
        associations1 (filter #(= false (:deleted %))
                             (search/find-concepts context search-params1))
        search-params2 (cutil/remove-nil-keys
                       {:concept-type :generic-association
                        :associated-concept-id concept-id
                        :exclude-metadata true
                        :latest true})
        associations2(filter #(= false (:deleted %))
                             (search/find-concepts context search-params2))
        associations (concat associations1 associations2)]
    (when (> (count associations) 0)
      (ingest-events/publish-event
       context
       (ingest-events/associations-update-event associations)))))

(defn- publish-tool-associations-update-event
  "Publish one concept-update-event for all non-tombstoned tool associations for the
  given tool concept; This is to trigger the reindexing of the associated collections in elastic
  search when tool is updated because tool info is indexed into the associated collections.
  Does nothing if the given concept is not a tool concept."
  [context concept-type concept-id]
  (when (= :tool concept-type)
    (let [search-params (cutil/remove-nil-keys
                         {:concept-type :tool-association
                          :tool-concept-id concept-id
                          :exclude-metadata true
                          :latest true})
          associations (filter #(= false (:deleted %))
                               (search/find-concepts context search-params))]
      (when (> (count associations) 0)
        (ingest-events/publish-event
         context
         (ingest-events/associations-update-event associations))))))

;; false implies creation of a non-tombstone revision
(defmethod save-concept-revision false
  [context concept]
  (trace "concept:" (keys concept))
  (trace "provider id:" (:provider-id concept))
  (cv/validate-concept concept)
  (let [db (util/context->db context)
        provider-id (or (:provider-id concept)
                        (when (contains? system-level-concept-types (:concept-type concept)) "CMR"))
        ;; Need this for tags/tag-associations since they don't have a provider-id in their
        ;; concept maps, but later processing requires it.
        concept (assoc concept :provider-id provider-id)
        provider (provider-service/get-provider-by-id context provider-id true)
        _ (validate-system-level-concept concept provider)
        concept (->> concept
                     (set-or-generate-concept-id db provider)
                     (set-created-at db provider))
        {:keys [concept-type concept-id]} concept]
    (validate-concept-revision-id db provider concept)
    ;; validate newly ingested variable contains collection info.
    (when (= :variable concept-type)
      (let [previous-concept (c/get-concept db concept-type provider concept-id)]
        (validate-new-variable-has-collection-info concept previous-concept)))

    (let [concept (->> concept
                       (set-or-generate-revision-id db provider)
                       (set-deleted-flag false)
                       (try-to-save db provider context))
          revision-id (:revision-id concept)]
      ;; publish tombstone delete event if the previous concept revision is a granule tombstone
      (when (and (= :granule concept-type)
                 (> revision-id 1))
        (let [previous-concept (c/get-concept db concept-type provider concept-id (- revision-id 1))]
          (when (util/is-tombstone? previous-concept)
            (ingest-events/publish-tombstone-delete-msg
             context concept-type concept-id revision-id))))

      ;; publish service/tool associations update event if applicable, i.e. when the concept is a service/tool,
      ;; so that the collections can be updated in elasticsearch with the updated service/tool info
      (publish-service-associations-update-event context concept-type concept-id)
      (publish-tool-associations-update-event context concept-type concept-id)
      (publish-generic-associations-update-event context concept-type concept-id)
      (ingest-events/publish-event
       context
       (ingest-events/concept-update-event concept))
      concept)))

(defn- delete-associated-tag-associations
  "Delete the tag associations associated with the given collection revision,
  no tag association deletion event is generated."
  [context coll-concept-id coll-revision-id]
  (delete-associations-for-collection-concept
   context :tag-association coll-concept-id coll-revision-id))

(defmulti force-delete-cascading-events
  "Performs the cascading events of the force deletion of a concept"
  (fn [context concept-type concept-id revision-id]
    concept-type))

(defmethod force-delete-cascading-events :collection
  [context concept-type concept-id revision-id]
  ;; delete the related tag associations and variable associations
  (delete-associated-tag-associations context concept-id revision-id)
  (delete-associations context concept-type concept-id revision-id :variable-association)
  (delete-associations context concept-type concept-id revision-id :service-association)
  (delete-associations context concept-type concept-id revision-id :tool-association)
  (delete-associations context concept-type concept-id revision-id :generic-association)
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :variable
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :subscription
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :tool
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :service
  [context concept-type concept-id revision-id]
  (ingest-events/publish-concept-revision-delete-msg context concept-id revision-id))

(defmethod force-delete-cascading-events :default
  [context concept-type concept-id revision-id]
  ;; does nothing in default
  nil)

(defn force-delete
  "Remove a revision of a concept from the database completely."
  [context concept-id revision-id force?]
  (let [db (util/context->db context)
        {:keys [concept-type provider-id]} (cu/parse-concept-id concept-id)
        provider (provider-service/get-provider-by-id context provider-id true)
        concept (c/get-concept db concept-type provider concept-id revision-id)]
    (if concept
      (if (and (not force?)
               (latest-revision? context concept-id revision-id))
        (errors/throw-service-error
         :bad-request
         (format (str "Cannot force delete the latest revision of a concept "
                      "[%s, %s], use regular delete instead.")
                 concept-id revision-id))
        (do
          (force-delete-cascading-events context concept-type concept-id revision-id)
          (c/force-delete db concept-type provider concept-id revision-id)))
      (cmsg/data-error :not-found
                       msg/concept-with-concept-id-and-rev-id-does-not-exist
                       concept-id
                       revision-id))
    {:concept-id concept-id
     :revision-id revision-id}))

(defn reset
  "Delete all concepts from the concept store and all providers."
  [context]
  (provider-service/reset-providers context)
  (c/reset (util/context->db context)))

(defn get-concept-id
  "Get a concept id for a given concept."
  [context concept-type provider-id native-id]
  (cu/validate-concept-type concept-type)
  (let [db (util/context->db context)
        provider (provider-service/get-provider-by-id context provider-id true)
        concept-id (c/get-concept-id db concept-type provider native-id)]
    (if concept-id
      concept-id
      (cmsg/data-error :not-found
                       msg/missing-concept-id
                       concept-type
                       provider-id
                       native-id))))

(defn- get-provider-to-collection-map
  "Returns a map of the provider ids to collection concepts that exist in the database."
  [context]
  (let [db (util/context->db context)]
    (into {} (pmap (fn [{:keys [provider-id] :as provider}]
                     [provider-id
                      (->> (c/find-latest-concepts db provider {:provider-id provider-id
                                                                :concept-type :collection})
                           (remove :deleted))])
                   (provider-db/get-providers db)))))

;; There's not sufficient integration tests for this. Filed CMR-1579
(defn get-provider-holdings
  "Gets provider holdings within Metadata DB"
  [context]
  (let [db (util/context->db context)
        ;; Create a map of provider to collection concepts
        provider-to-collections (get-provider-to-collection-map context)
        ;; Get a map of provider id to counts of granules per collection concept id
        provider-to-count-maps (into {}
                                     (pmap (fn [{:keys [provider-id] :as provider}]
                                             [provider-id (c/get-concept-type-counts-by-collection
                                                            db :granule provider)])
                                           (provider-db/get-providers db)))]
    (for [[provider-id collections] provider-to-collections
          collection collections
          :let [concept-id (:concept-id collection)
                granule-count (get-in provider-to-count-maps
                                      [provider-id concept-id]
                                      0)]]
      {:provider-id provider-id
       :concept-id concept-id
       :granule-count granule-count
       :entry-title (get-in collection [:extra-fields :entry-title])})))

(defn delete-expired-concepts
  "Delete concepts that have not been deleted and have a delete-time before now"
  [context provider concept-type]
  (let [db (util/context->db context)
        ;; atom to store concepts to skip on subsequent recursions
        failed-concept-ids (atom #{})
        ;; return expired concepts which have not previously failed
        get-expired (fn []
                      (remove
                        #(contains? @failed-concept-ids (:concept-id %))
                        (c/get-expired-concepts db provider concept-type)))]
    (loop []
      (when-let [expired-concepts (seq (get-expired))]
        (info "Deleting expired" (name concept-type) "concepts:" (pr-str (map :concept-id expired-concepts)))
        (doseq [c expired-concepts]
          (let [tombstone (-> c
                              (update-in [:revision-id] inc)
                              (assoc :deleted true
                                     :metadata ""
                                     :revision-date (p/clj-time->date-time-str (time-keeper/now))))]
            (try
              (try-to-save db provider context tombstone)
              (ingest-events/publish-event
               context (ingest-events/concept-expire-event c))
              (catch clojure.lang.ExceptionInfo e
                ;; Re-throw anything other than a simple conflict.
                (when-not (-> e ex-data :type (= :conflict))
                  (throw e))
                ;; If an update comes in for one of the items we are
                ;; deleting, it will result in a conflict, in that
                ;; case we just want to log a warning and store the
                ;; failed concept-id in order avoid an infinite loop.
                (warn e "Conflict when saving expired concept tombstone")
                (swap! failed-concept-ids conj (:concept-id c))))))
        (recur)))))

(defn- call-force-deletes
  "Calls functions that do the deletion of concepts or that publish events to message queue."
  [context db provider concept-type tombstone-delete? concept-id-revision-id-tuples concept-truncation-batch-size]
  (when concept-id-revision-id-tuples
    (info "Deleting" (count concept-id-revision-id-tuples)
          "old concept revisions for provider" (:provider-id provider))
    (when (and tombstone-delete?
               (= :granule concept-type))
      ;; Remove any reference to granule from deleted-granule index
      (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
        (ingest-events/publish-tombstone-delete-msg context concept-type concept-id revision-id)))
    (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
      ;; performs the cascading delete actions first
      (force-delete-cascading-events context concept-type concept-id (long revision-id)))
    (c/force-delete-concepts db provider concept-type concept-id-revision-id-tuples)))

(defn force-delete-with
  "Continually force deletes concepts using the given function concept-id-revision-id-tuple-finder
  to find concept id revision id tuples to delete. Stops once the function returns an empty set."
  [context provider concept-type tombstone-delete? concept-id-revision-id-tuple-finder concept-truncation-batch-size]
  (let [db (util/context->db context)]
    (loop [concept-id-revision-id-tuples (seq (concept-id-revision-id-tuple-finder))]
      (if (< (count concept-id-revision-id-tuples) concept-truncation-batch-size)
        (call-force-deletes
          context db provider concept-type tombstone-delete? concept-id-revision-id-tuples concept-truncation-batch-size)
        (do
          (call-force-deletes
            context db provider concept-type tombstone-delete? concept-id-revision-id-tuples concept-truncation-batch-size)
          (recur (seq (concept-id-revision-id-tuple-finder))))))))

(defn delete-old-revisions
  "Delete concepts to keep a fixed number of revisions around. It also deletes old tombstones that
  are older than a fixed number of days and any prior revisions of the deleted tombstone."
  [context provider concept-type]
  (let [db (util/context->db context)
        concept-type-name (str (name concept-type) "s")
        tombstone-cut-off-date (t/minus (time-keeper/now) (t/days (days-to-keep-tombstone)))]

    ;; We only want to publish the deleted-tombstone event in the case where a granule tombstone is
    ;; being cleaned up, not when an old revision is being removed, because the case of old revision,
    ;; a deleted-tombstone even would have been published already.
    (info "Starting deletion of old" concept-type-name "for provider" (:provider-id provider))
    (force-delete-with
      context provider concept-type false
      #(c/get-old-concept-revisions
         db
         provider
         concept-type
         (get num-revisions-to-keep-per-concept-type
              concept-type)
         concept-truncation-batch-size)
      concept-truncation-batch-size)

    (info "Starting deletion of tombstoned" concept-type-name "for provider" (:provider-id provider))
    (force-delete-with
      context provider concept-type true
      #(c/get-tombstoned-concept-revisions
         db
         provider
         concept-type
         tombstone-cut-off-date
         concept-truncation-batch-size)
      concept-truncation-batch-size)))
