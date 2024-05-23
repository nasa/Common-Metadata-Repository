(ns cmr.search.services.generic-association-service
  "Provides functions for associating and dissociating generic concepts."
  (:require
   [clojure.set :refer [rename-keys]]
   [cmr.common.api.context :as cmn-context]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :as log :refer (debug info)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.metadata-db.services.concept-service :as mdb-cs]
   [cmr.metadata-db.services.search-service :as mdb-ss]
   [cmr.search.services.association-service :as assoc-service]
   [cmr.search.services.association-validation :as assoc-validation]
   [cmr.search.services.messages.association-messages :as assoc-msg]))

(def ^:private native-id-separator-character
  "This is the separator character used when creating the native id for an association."
  "/")

(def ^:private generic-association-conflict-error-message
  "Failed to %s %s [%s] with %s [%s] because it conflicted with a concurrent %s on the
  same %s and %s. This means that someone is sending the same request to the CMR at the
  same time.")

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
                       :user-id (cmn-context/context->user-id mdb-context assoc-msg/associations-need-token)
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
                revision-id originator-id native-id user-id data]} generic-association]
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
         concept-id :concept-id
         errors :errors} association
        native-id (association->native-id association)
        source-concept-type-str (name (concepts/concept-id->type source-concept-id))
        concept-type-str (name (concepts/concept-id->type concept-id))
        assoc-concept-type (keyword "generic-association")
        association (-> association
                        (assoc :native-id native-id)
                        (assoc :user-id (cmn-context/context->user-id mdb-context assoc-msg/associations-need-token)))
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
  [context concept-type concept-id revision-id associations operation-type]
  (let [revision-id (when revision-id
                      (read-string revision-id))
        concept {:concept-id concept-id :revision-id revision-id}
        ;;validate the user has the permission to access the concept
        ;;and the concept is not tomb-stoned.
        _ (assoc-validation/validate-concept-for-generic-associations context concept)
        associations (->> associations
                          (assoc-validation/validate-generic-association-combination-types concept)
                          (assoc-validation/validate-no-same-concept-generic-association concept)
                          (assoc-validation/validate-generic-association-types concept))
        [validation-time associations]
        (util/time-execution
         (assoc-validation/validate-generic-associations
          context concept-type concept-id revision-id associations operation-type))]
    (debug "link-to-concepts validation-time:" validation-time)
    (update-generic-associations context concept associations operation-type)))

(defn- separate-out-concept-type
  "Separate the associations out for the passed in concept type from the
  rest of the associations and put them into a map."
  [concept-type associations]
  {:concept-assoc (seq (filter #(= (concepts/concept-id->type (:concept-id %)) concept-type) associations))
   :the-rest (seq (filter #(not (= (concepts/concept-id->type (:concept-id %)) concept-type)) associations))})

(defn create-collection-associated-concepts
  "Set the collections concept-id revision-id and data so that we can
  invert the association and call the tool or service to collection
  assocation function."
  [context coll-concept-id coll-revision-id concept-assoc operation-type]
  (let [coll-assoc (list (util/remove-nil-keys
                          {:concept-id coll-concept-id
                           :revision-id coll-revision-id
                           :data (:data concept-assoc)}))]
    (assoc-service/link-to-collections context
                                       (concepts/concept-id->type (:concept-id concept-assoc))
                                       (:concept-id concept-assoc)
                                       coll-assoc
                                       operation-type)))

(defn concept-appropriate-links-to-concepts
  "Separate out from a list of associations the tool or service concept to collection associations.
  Then ingest/delete those association. Then ingest/delete the rest of the assocations.
  Return the concatenated results."
  [context concept-type concept-id revision-id associations operation-type swap-keyword]
  (let [to-be-assoc (separate-out-concept-type :collection associations)
        coll-assoc (when (:concept-assoc to-be-assoc)
                     (assoc-service/link-to-collections context concept-type concept-id (:concept-assoc to-be-assoc) operation-type))
        updated-coll-assoc (map #(rename-keys % {swap-keyword :generic-association}) coll-assoc)
        other-assoc (when (:the-rest to-be-assoc)
                      (link-to-concepts context concept-type concept-id revision-id (:the-rest to-be-assoc) operation-type))]
    (concat other-assoc updated-coll-assoc)))

(defn call-appropriate-link-to-concepts
  "For tool or service to collection assocations, separate them out and call the original assocation functions
  otherwise go ahead and call the generic association functions.
  Return the concatenated results."
  [context concept-type concept-id revision-id associations-json operation-type]
  (let [associations (assoc-validation/associations-json->associations associations-json)]
    (case concept-type
      :tool (concept-appropriate-links-to-concepts context concept-type concept-id revision-id associations operation-type :tool-association)
      :service (concept-appropriate-links-to-concepts context concept-type concept-id revision-id associations operation-type :service-association)
      :variable (concept-appropriate-links-to-concepts context concept-type concept-id revision-id associations operation-type :variable-association)
      :collection (let [tool-to-be-assoc (separate-out-concept-type :tool associations)
                        tool-assoc (when (:concept-assoc tool-to-be-assoc)
                                     (flatten
                                      (map #(create-collection-associated-concepts context concept-id revision-id % operation-type) (:concept-assoc tool-to-be-assoc))))
                        updated-tool-assoc (map #(rename-keys % {:tool-association :generic-association}) tool-assoc)
                        service-to-be-assoc (when (:the-rest tool-to-be-assoc)
                                             (separate-out-concept-type :service (:the-rest tool-to-be-assoc)))
                        service-assoc (when (:concept-assoc service-to-be-assoc)
                                        (flatten
                                         (map #(create-collection-associated-concepts context concept-id revision-id % operation-type) (:concept-assoc service-to-be-assoc))))
                        updated-service-assoc (map #(rename-keys % {:service-association :generic-association}) service-assoc)
                        variable-to-be-assoc (when (:the-rest service-to-be-assoc)
                                               (separate-out-concept-type :variable (:the-rest service-to-be-assoc)))
                        variable-assoc (when (:concept-assoc variable-to-be-assoc)
                                        (flatten
                                         (map #(create-collection-associated-concepts context concept-id revision-id % operation-type) (:concept-assoc variable-to-be-assoc))))
                        other-assoc (when (:the-rest variable-to-be-assoc)
                                     (link-to-concepts context concept-type concept-id revision-id (:the-rest variable-to-be-assoc) operation-type))]
                    (concat other-assoc updated-tool-assoc updated-service-assoc variable-assoc))
      (link-to-concepts context concept-type concept-id revision-id associations operation-type))))

(defn associate-to-concepts
  "Associates the given concept by concept-type, concept-id and revision-id to
  the given list of associations in json."
  [context concept-type concept-id revision-id associations-json]
  (call-appropriate-link-to-concepts context concept-type concept-id revision-id associations-json :insert))

(defn dissociate-from-concepts
  "Dissociates the given concept by concept-type, concept-id and revision-id from
  the given list of associations in json."
  [context concept-type concept-id revision-id associations-json]
  (call-appropriate-link-to-concepts context concept-type concept-id revision-id associations-json :delete))
