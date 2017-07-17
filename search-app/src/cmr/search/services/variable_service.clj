(ns cmr.search.services.variable-service
  "Provides functions for associating and dissociating variables to collections"
  (:require
    [clojure.edn :as edn]
    [cmr.common.log :as log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as util]
    [cmr.metadata-db.services.concept-service :as mdb-cs]
    [cmr.metadata-db.services.search-service :as mdb-ss]
    [cmr.search.services.association-validation :as assoc-validation]
    [cmr.search.services.messages.association-messages :as assoc-msg]
    [cmr.transmit.metadata-db :as mdb]
    [cmr.umm-spec.umm-spec-core :as spec]))

(def ^:private native-id-separator-character
  "This is the separator character used when creating the native id for a variable."
  "/")

(def ^:private association-conflict-error-message
  "Failed to %s variable [%s] with collection [%s] because it conflicted with a concurrent %s on the
  same variable and collection. This means that someone is sending the same request to the CMR at the
  same time.")

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (util/lazy-get context :user-id)
    (errors/throw-service-error
     :unauthorized "Variables cannot be modified without a valid user token.")))

(defn- save-concept-in-mdb
  "Save the given concept in metadata-db using the given embedded metadata-db context."
  [mdb-context concept]
  (let [{:keys [concept-id revision-id]} (mdb-cs/save-concept-revision mdb-context concept)]
    {:concept-id concept-id, :revision-id revision-id}))

(defn- delete-variable-association
  "Delete the variable association with the given native-id using the given embedded metadata-db
  context."
  [mdb-context native-id]
  (let [existing-concept (first (mdb-ss/find-concepts mdb-context
                                                      {:concept-type :variable-association
                                                       :native-id native-id
                                                       :exclude-metadata true
                                                       :latest true}))
        concept-id (:concept-id existing-concept)]
    (if concept-id
      (if (:deleted existing-concept)
        {:message {:warnings [(format "Variable association [%s] is already deleted." concept-id)]}}
        (let [concept {:concept-type :variable-association
                       :concept-id concept-id
                       :user-id (context->user-id mdb-context)
                       :deleted true}]
          (save-concept-in-mdb mdb-context concept)))
      {:message {:warnings [(assoc-msg/delete-association-not-found :variable native-id)]}})))

(defn- variable-association->native-id
  "Returns the native id of the given variable association."
  [variable-association]
  (let [{coll-concept-id :concept-id
         coll-revision-id :revision-id
         variable-name :variable-name} variable-association
        native-id (str variable-name native-id-separator-character coll-concept-id)]
    (if coll-revision-id
      (str native-id native-id-separator-character coll-revision-id)
      native-id)))

(defn- variable-association->concept-map
  "Returns the concept map for inserting into metadata-db for the given variable association."
  [variable-association]
  (let [{:keys [variable-name originator-id native-id user-id data errors]
         coll-concept-id :concept-id
         coll-revision-id :revision-id} variable-association]
    {:concept-type :variable-association
     :native-id native-id
     :user-id user-id
     :format mt/edn
     :metadata (pr-str
                 (util/remove-nil-keys
                   {:variable-name variable-name
                    :originator-id originator-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id
                    :data data}))
     :extra-fields {:variable-name variable-name
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id}}))

(defn- update-variable-association
  "Based on the input operation type (:insert or :delete), insert or delete the given variable
  association using the embedded metadata-db, returns the variable association result in the format of
  {variable_association: {concept_id: VA1-CMR, revision_id: 1},
  associated_item: {concept_id: C5-PROV1 revision_id: 2}}
  or
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  associated_item: {concept_id: C6-PROV1}}."
  [mdb-context variable-association operation]
  ;; save each variable-association if there is no errors on it, otherwise returns the errors.
  (let [{coll-concept-id :concept-id
         coll-revision-id :revision-id
         errors :errors} variable-association
        native-id (variable-association->native-id variable-association)
        variable-association (-> variable-association
                            (assoc :native-id native-id)
                            (assoc :user-id (context->user-id mdb-context)))
        associated-item (util/remove-nil-keys
                     {:concept-id coll-concept-id :revision-id coll-revision-id})]
    (if (seq errors)
      {:errors errors :associated-item associated-item}
      (try
        (let [{:keys [concept-id revision-id message]} ;; only delete-variable-association could potentially return message
              (if (= :insert operation)
                (save-concept-in-mdb mdb-context (variable-association->concept-map variable-association))
                (delete-variable-association mdb-context native-id))]
          (if (some? message)
            (merge {:associated-item associated-item} message)
            {:variable-association {:concept-id concept-id :revision-id revision-id}
             :associated-item associated-item}))
        (catch clojure.lang.ExceptionInfo e ; Report a specific error in the case of a conflict, otherwise throw error
          (if (= :conflict (:type (ex-data e)))
            {:errors (format association-conflict-error-message
                        (if (= :insert operation) "associate" "dissociate")
                        (:variable-name variable-association)
                        coll-concept-id
                        (if (= :insert operation) "association" "dissociation"))
             :associated-item associated-item}
            (throw e)))))))

(defn- update-variable-associations
  "Based on the input operation type (:insert or :delete), insert or delete variable associations,
  returns the variable association result in the format of
  [{variable_association: {concept_id: VA1-CMR, revision_id: 1},
  associated_item: {concept_id: C5-PROV1 revision_id: 2}},
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  associated_item: {concept_id: C6-PROV1}}]."
  [context variable-concept variable-associations operation]
  (let [variable-name (get-in variable-concept [:extra-fields :variable-name])
        existing-variable (spec/parse-metadata context
                                               :variable
                                               (:format variable-concept)
                                               (:metadata variable-concept))
        {:keys [originator-id]} existing-variable
        mdb-context (assoc context :system
                           (get-in context [:system :embedded-systems :metadata-db]))
        [t1 result] (util/time-execution (util/fast-map
                                          (fn [association]
                                            (update-variable-association
                                             mdb-context
                                             (merge association {:variable-name variable-name
                                                                 :originator-id originator-id})
                                             operation))
                                          variable-associations))]
    (info "update-variable-associations:" t1)
    result))

(defn- validate-variable-associations
  "Validates the variable association for the given variable name and variable associations based
  on the operation type, which is either :insert or :delete. Returns the variable associations
  with errors found appended to them.
  If the provided variable associations fail the basic rules validation (e.g. empty variable
  associations, conflicts within the request), throws a service error."
  [context operation-type variable-name variable-associations]
  (assoc-validation/validate-associations
   context :variable variable-name variable-associations operation-type))

(defn- fetch-variable-concept
  "Fetches the latest version of a variable concept by variable-name"
  [context variable-name]
  (if-let [concept (mdb/find-latest-concept context
                                            {:native-id variable-name
                                             :latest true}
                                            :variable)]
    (if (:deleted concept)
      (errors/throw-service-error
       :not-found
       (format "Variable with variable-name [%s] was deleted." variable-name))
      concept)
    (errors/throw-service-error
     :not-found
     (format "Variable could not be found with variable-name [%s]" variable-name))))

(defn- link-variable-to-collections
  "Associate/Dissociate a variable to a list of collections in the variable associations json
   based on the given operation type. The ooperation type can be either :insert or :delete.
   Throws service error if the variable with the given variable key is not found."
  [context variable-name variable-associations-json operation-type]
  (let [variable-concept (fetch-variable-concept context variable-name)
        ;; Variable association will reuse the same json schema for tag associations.
        ;; The only difference between variable association and tag association is variable
        ;; association uses variable-name as the key and tag association uses tag-key as the key.
        variable-associations (assoc-validation/associations-json->associations
                               variable-associations-json)

        [validation-time variable-associations]
        (util/time-execution
         (validate-variable-associations
          context operation-type variable-name variable-associations))]

    (debug "link-variable-to-collections validation-time:" validation-time)
    (update-variable-associations context variable-concept variable-associations operation-type)))

(defn associate-variable-to-collections
  "Associates a variable to the given list of variable associations in json."
  [context variable-name variable-associations-json]
  (link-variable-to-collections context variable-name variable-associations-json :insert))

(defn dissociate-variable-to-collections
  "Dissociates a variable from the given list of variable associations in json."
  [context variable-name variable-associations-json]
  (link-variable-to-collections context variable-name variable-associations-json :delete))
