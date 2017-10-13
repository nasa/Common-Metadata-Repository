(ns cmr.search.services.association-service
  "Provides functions for associating and dissociating variables/services to collections"
  (:require
   [clojure.string :as string]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.metadata-db.services.concept-service :as mdb-cs]
   [cmr.metadata-db.services.search-service :as mdb-ss]
   [cmr.search.services.association-validation :as assoc-validation]
   [cmr.search.services.messages.association-messages :as assoc-msg]
   [cmr.transmit.metadata-db :as mdb]))

(def ^:private native-id-separator-character
  "This is the separator character used when creating the native id for an association."
  "/")

(def ^:private association-conflict-error-message
  "Failed to %s %s [%s] with collection [%s] because it conflicted with a concurrent %s on the
  same %s and collection. This means that someone is sending the same request to the CMR at the
  same time.")

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (util/lazy-get context :user-id)
    (errors/throw-service-error
     :unauthorized "Associations cannot be modified without a valid user token.")))

(defn- save-concept-in-mdb
  "Save the given concept in metadata-db using the given embedded metadata-db context."
  [mdb-context concept]
  (let [{:keys [concept-id revision-id]} (mdb-cs/save-concept-revision mdb-context concept)]
    {:concept-id concept-id, :revision-id revision-id}))

(defn- delete-association
  "Delete the association with the given native-id using the given embedded metadata-db context."
  [mdb-context source-concept-type native-id]
  (let [source-concept-type-str (name source-concept-type)
        assoc-concept-type (keyword (str source-concept-type-str "-association"))
        existing-concept (first (mdb-ss/find-concepts mdb-context
                                                      {:concept-type assoc-concept-type
                                                       :native-id native-id
                                                       :exclude-metadata true
                                                       :latest true}))
        concept-id (:concept-id existing-concept)]
    (if concept-id
      (if (:deleted existing-concept)
        {:message {:warnings [(format "%s association [%s] is already deleted."
                                      (string/capitalize source-concept-type-str) concept-id)]}}
        (let [concept {:concept-type assoc-concept-type
                       :concept-id concept-id
                       :user-id (context->user-id mdb-context)
                       :deleted true}]
          (save-concept-in-mdb mdb-context concept)))
      {:message {:warnings [(assoc-msg/delete-association-not-found
                             source-concept-type native-id)]}})))

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

(defmulti association->concept-map
  "Returns the concept-map for inserting into metadata-db for the given association.
  It is keyed off on the source concept type of the association, e.g. :variable, :service."
  (fn [association]
    (:source-concept-type association)))

(defmethod association->concept-map :variable
  [variable-association]
  (let [{:keys [source-concept-id originator-id native-id user-id data errors]
         coll-concept-id :concept-id
         coll-revision-id :revision-id} variable-association]
    {:concept-type :variable-association
     :native-id native-id
     :user-id user-id
     :format mt/edn
     :metadata (pr-str
                 (util/remove-nil-keys
                   {:variable-concept-id source-concept-id
                    :originator-id originator-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id
                    :data data}))
     :extra-fields {:variable-concept-id source-concept-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id}}))

(defmethod association->concept-map :service
  [service-association]
  (let [{:keys [source-concept-id originator-id native-id user-id data errors]
         coll-concept-id :concept-id
         coll-revision-id :revision-id} service-association]
    {:concept-type :service-association
     :native-id native-id
     :user-id user-id
     :format mt/edn
     :metadata (pr-str
                 (util/remove-nil-keys
                   {:service-concept-id source-concept-id
                    :originator-id originator-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id
                    :data data}))
     :extra-fields {:service-concept-id source-concept-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id}}))

(defn- update-association
  "Based on the input operation type (:insert or :delete), insert or delete the given association
  using the embedded metadata-db, returns the association result in the format of
  {variable_association: {concept_id: VA1-CMR, revision_id: 1},
  associated_item: {concept_id: C5-PROV1 revision_id: 2}}
  or
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  associated_item: {concept_id: C6-PROV1}}."
  [mdb-context association operation]
  ;; save each association if there is no errors on it, otherwise returns the errors.
  (let [{coll-concept-id :concept-id
         coll-revision-id :revision-id
         source-concept-id :source-concept-id
         source-concept-type :source-concept-type
         errors :errors} association
        native-id (association->native-id association)
        source-concept-type-str (name source-concept-type)
        assoc-concept-type (keyword (str source-concept-type-str "-association"))
        association (-> association
                        (assoc :native-id native-id)
                        (assoc :user-id (context->user-id mdb-context)))
        associated-item (util/remove-nil-keys
                         {:concept-id coll-concept-id :revision-id coll-revision-id})]
    (if (seq errors)
      {:errors errors :associated-item associated-item}
      (try
        (let [{:keys [concept-id revision-id message]} ;; only delete-association could potentially return message
              (if (= :insert operation)
                (save-concept-in-mdb
                 mdb-context (association->concept-map association))
                (delete-association mdb-context source-concept-type native-id))]
          (if (some? message)
            (merge {:associated-item associated-item} message)
            {assoc-concept-type {:concept-id concept-id :revision-id revision-id}
             :associated-item associated-item}))
        (catch clojure.lang.ExceptionInfo e ; Report a specific error in the case of a conflict, otherwise throw error
          (if (= :conflict (:type (ex-data e)))
            {:errors (format association-conflict-error-message
                             (if (= :insert operation) "associate" "dissociate")
                             source-concept-type-str
                             source-concept-id
                             coll-concept-id
                             (if (= :insert operation) "association" "dissociation")
                             source-concept-type-str)
             :associated-item associated-item}
            (throw e)))))))

(defn- update-associations
  "Based on the input operation type (:insert or :delete), insert or delete associations,
  returns the association result in the format of
  [{variable_association: {concept_id: VA1-CMR, revision_id: 1},
  associated_item: {concept_id: C5-PROV1 revision_id: 2}},
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  associated_item: {concept_id: C6-PROV1}}]."
  [context concept associations operation]
  (let [{:keys [concept-id concept-type]} concept
        mdb-context (assoc context :system
                           (get-in context [:system :embedded-systems :metadata-db]))
        [t1 result] (util/time-execution (util/fast-map
                                          (fn [association]
                                            (update-association
                                             mdb-context
                                             (merge association
                                                    {:source-concept-id concept-id
                                                     :source-concept-type concept-type})
                                             operation))
                                          associations))]
    (info "update-associations:" t1)
    result))


(defn- fetch-concept
  "Fetches the latest version of a concept by concept-id with proper error handling."
  [context concept-type concept-id]
  (if-let [concept (mdb/find-latest-concept context
                                            {:concept-id concept-id
                                             :latest true}
                                            concept-type)]
    (if (:deleted concept)
      (errors/throw-service-error
       :not-found
       (format "%s with concept id [%s] was deleted."
               (string/capitalize (name concept-type)) concept-id))
      concept)
    (errors/throw-service-error
     :not-found
     (format "%s could not be found with concept id [%s]"
             (string/capitalize (name concept-type)) concept-id))))

(defn- link-to-collections
  "Associate/Dissociate a variable/service to a list of collections in the associations json
   based on the given operation type. The operation type can be either :insert or :delete.
   Throws service error if the variable/service with the given concept-id is not found."
  [context concept-type concept-id associations-json operation-type]
  (let [concept (fetch-concept context concept-type concept-id)
        ;; Variable/Service association will reuse the same json schema for tag associations.
        ;; The only difference between variable/service association and tag association is
        ;; variable/service association uses concept-id as the key and tag association
        ;; uses tag-key as the key.
        associations (assoc-validation/associations-json->associations associations-json)
        [validation-time associations]
        (util/time-execution
         (assoc-validation/validate-associations
          context concept-type concept-id associations operation-type))]

    (debug "link-to-collections validation-time:" validation-time)
    (update-associations context concept associations operation-type)))

(defn associate-to-collections
  "Associates the given concept by concept-type and concept-id to
  the given list of associations in json."
  [context concept-type concept-id associations-json]
  (link-to-collections context concept-type concept-id associations-json :insert))

(defn dissociate-from-collections
  "Dissociates the given concept by concept-type and concept-id from
  the given list of associations in json."
  [context concept-type concept-id associations-json]
  (link-to-collections context concept-type concept-id associations-json :delete))
