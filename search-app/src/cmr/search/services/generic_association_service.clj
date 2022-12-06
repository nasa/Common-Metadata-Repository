(ns cmr.search.services.generic-association-service
  "Provides functions for associating and dissociating generic concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
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

(def ^:private generic-association-conflict-error-message
  "Failed to %s %s [%s] with %s [%s] because it conflicted with a concurrent %s on the
  same %s and %s. This means that someone is sending the same request to the CMR at the
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

(defn- delete-generic-association
  "Delete the association with the given native-id using the given embedded metadata-db context."
  [mdb-context native-id]
  (let [assoc-concept-type (keyword "generic-association")
        existing-concept (first (mdb-ss/find-concepts mdb-context
                                                      {:concept-type assoc-concept-type
                                                       :native-id native-id
                                                       :exclude-metadata true
                                                       :latest true}))
        concept-id (:concept-id existing-concept)]
    (if concept-id
      (if (:deleted existing-concept)
        {:message {:warnings [(format "Generic association [%s] is already deleted."
                                      concept-id)]}}
        (let [concept {:concept-type assoc-concept-type
                       :concept-id concept-id
                       :user-id (context->user-id mdb-context)
                       :deleted true}]
          (save-concept-in-mdb mdb-context concept)))
      {:message {:warnings [(assoc-msg/delete-generic-association-not-found
                             native-id)]}})))

(defn- association->native-id
  "Returns the native id of the given association."
  [association]
  (let [{source-concept-id :source-concept-id
         source-revision-id :source-revision-id
         concept-id :concept-id
         revision-id :revision-id} association
        source-part (if source-revision-id
                      (str source-concept-id
                           native-id-separator-character
                           source-revision-id)
                      source-concept-id)
        dest-part (if revision-id
                    (str concept-id
                         native-id-separator-character
                         revision-id)
                    concept-id)]
     (str source-part native-id-separator-character dest-part)))

(defn generic-association->concept-map
  "Returns the concept-map for inserting into metadata-db for the given association."
  [generic-association]
  (let [{:keys [source-concept-id source-revision-id concept-id
                revision-id originator-id native-id user-id data errors]} generic-association]
    {:concept-type :generic-association
     :native-id native-id
     :user-id user-id
     :format mt/edn
     :metadata (pr-str
                 (util/remove-nil-keys
                   {:source-concept-identifier source-concept-id
                    :source-revision-id source-revision-id
                    :originator-id originator-id
                    :associated-concept-id concept-id
                    :associated-revision-id revision-id
                    :data data}))
     :extra-fields {:source-concept-identifier source-concept-id
                    :source-revision-id source-revision-id
                    :associated-concept-id concept-id
                    :associated-revision-id revision-id}}))

(defn- sort-association
  "Given the source and destination concept-id and revision-id in the
  association, rearrange them so that when sort the source and destination
  concept-ids, source always appears first. The reason for the rearrangement
  is that the same association between two concepts will be treated the same
  in the database, no matter which direction the two concepts appear in the
  request."
  [association]
  (let [{dest-concept-id :concept-id
         dest-revision-id :revision-id
         source-concept-id :source-concept-id
         source-revision-id :source-revision-id} association]
    (if (= dest-concept-id (first (sort [source-concept-id dest-concept-id])))
      ;;switch source and destination.
      (-> association
          (assoc :source-concept-id dest-concept-id)
          (assoc :source-revision-id dest-revision-id)
          (assoc :concept-id source-concept-id)
          (assoc :revision-id source-revision-id))
      association)))

(defn- update-generic-association
  "Based on the input operation type (:insert or :delete), insert or delete the given association
  using the embedded metadata-db, returns the association result in the format of
  {generic_association: {concept_id: GA1-CMR, revision_id: 1},
  associated_item: {concept_id: C5-PROV1 revision_id: 2}}
  or
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  associated_item: {concept_id: C6-PROV1}}."
  [mdb-context association operation]
  ;; save each association if there is no errors on it, otherwise returns the errors.
  (let [associated-item-concept-id (:concept-id association)
        revision-id (:revision-id association)
        associated-item-revision-id (if (string? revision-id)
                                      (read-string revision-id)
                                      revision-id)
        association (sort-association association)
        {source-concept-id :source-concept-id
         source-revision-id :source-revision-id
         concept-id :concept-id
         revision-id :revision-id
         errors :errors} association
        native-id (association->native-id association)
        source-concept-type-str (name (concepts/concept-id->type source-concept-id))
        concept-type-str (name (concepts/concept-id->type concept-id))
        assoc-concept-type (keyword "generic-association")
        association (-> association
                        (assoc :native-id native-id)
                        (assoc :user-id (context->user-id mdb-context)))
        associated-item (util/remove-nil-keys
                         {:concept-id associated-item-concept-id
                          :revision-id associated-item-revision-id})]
    (if (seq errors)
      {:errors errors :associated-item associated-item}
      (try
        (let [{generic-assoc-concept-id :concept-id
               generic-assoc-revision-id :revision-id
               message :message} ;; only delete-association could potentially return message
              (if (= :insert operation)
                (save-concept-in-mdb
                 mdb-context (generic-association->concept-map association))
                (delete-generic-association mdb-context native-id))]
          (if (some? message)
            (merge {:associated-item associated-item} message)
            {assoc-concept-type {:concept-id generic-assoc-concept-id
                                 :revision-id generic-assoc-revision-id}
             :associated-item associated-item}))
        (catch clojure.lang.ExceptionInfo e ; Report a specific error in the case of a conflict, otherwise throw error
          (let [exception-data (ex-data e)
                type (:type exception-data)
                errors (:errors exception-data)]
            (cond
              (= :conflict type)  {:errors (format generic-association-conflict-error-message
                                                   (if (= :insert operation) "associate" "dissociate")
                                                   source-concept-type-str
                                                   source-concept-id
                                                   concept-type-str
                                                   concept-id
                                                   (if (= :insert operation) "association" "dissociation")
                                                   source-concept-type-str
                                                   concept-type-str)
                                   :associated-item associated-item}
              (= :can-not-associate type) {:errors errors
                                           :associated-item associated-item}
              :else (throw e))))))))

(defn- update-generic-associations
  "Based on the input operation type (:insert or :delete), insert or delete associations,
  returns the association result in the format of
  [{generic_association: {concept_id: GA1-CMR, revision_id: 1},
  associated_item: {concept_id: C5-PROV1 revision_id: 2}},
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  associated_item: {concept_id: C6-PROV1}}]."
  [context concept associations operation]
  (let [{:keys [concept-id revision-id]} concept
        mdb-context (assoc context :system
                           (get-in context [:system :embedded-systems :metadata-db]))
        [t1 result] (util/time-execution (util/fast-map
                                          (fn [association]
                                            (update-generic-association
                                             mdb-context
                                             (merge association
                                                    {:source-concept-id concept-id
                                                     :source-revision-id revision-id})
                                             operation))
                                          associations))]
    (info "update-generic-associations:" t1)
    result))

(defn- link-to-concepts
  "Associate/Dissociate a concept to a list of concepts in the associations json
   based on the given operation type. The operation type can be either :insert or :delete.
   Throws service error if the concept with the given concept-id and revision-id is not found."
  [context concept-type concept-id revision-id associations-json operation-type]
  (let [revision-id (when revision-id
                      (read-string revision-id))
        concept {:concept-id concept-id :revision-id revision-id}
        ;;validate the user has the permission to access the concept
        ;;and the concept is not tomb-stoned.
        _ (assoc-validation/validate-concept-for-generic-associations context concept)
        associations (->> associations-json
                          (assoc-validation/associations-json->associations)
                          (assoc-validation/validate-generic-association-combination-types concept)
                          (assoc-validation/validate-no-same-concept-generic-association concept)
                          (assoc-validation/validate-generic-association-types concept))
        [validation-time associations]
        (util/time-execution
         (assoc-validation/validate-generic-associations
          context concept-type concept-id revision-id associations operation-type))]
    (debug "link-to-concepts validation-time:" validation-time)
    (update-generic-associations context concept associations operation-type)))

(defn associate-to-concepts
  "Associates the given concept by concept-type, concept-id and revision-id to
  the given list of associations in json."
  [context concept-type concept-id revision-id associations-json]
  (link-to-concepts context concept-type concept-id revision-id associations-json :insert))

(defn dissociate-from-concepts
  "Dissociates the given concept by concept-type, concept-id and revision-id from
  the given list of associations in json."
  [context concept-type concept-id revision-id associations-json]
  (link-to-concepts context concept-type concept-id revision-id associations-json :delete))
