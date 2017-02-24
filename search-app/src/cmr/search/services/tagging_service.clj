(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common.concepts :as concepts]
    [cmr.common.log :as log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.services.messages :as cmsg]
    [cmr.common.util :as util]
    [cmr.metadata-db.services.concept-service :as mdb-cs]
    [cmr.metadata-db.services.search-service :as mdb-ss]
    [cmr.search.services.json-parameters.conversion :as jp]
    [cmr.search.services.query-service :as query-service]
    [cmr.search.services.tagging.tag-association-validation :as av]
    [cmr.search.services.tagging.tag-validation :as tv]
    [cmr.search.services.tagging.tagging-service-messages :as msg]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.transmit.metadata-db :as mdb]))

(def ^:private native-id-separator-character
  "This is the separator character used when creating the native id for a tag."
  "/")

(def ^:private association-conflict-error-message
  "Failed to %s tag [%s] with collection [%s] because it conflicted with a concurrent %s on the same tag and collection. This means that someone is sending the same request to the CMR at the same time.")

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (util/lazy-get context :user-id)
    (errors/throw-service-error :unauthorized msg/token-required-for-tag-modification)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB Concept Map Manipulation

(defn- tag->new-concept
  "Converts a tag into a new concept that can be persisted in metadata db."
  [tag]
  {:concept-type :tag
   :native-id (:tag-key tag)
   :metadata (pr-str tag)
   :user-id (:originator-id tag)
   ;; The first version of a tag should always be revision id 1. We always specify a revision id
   ;; when saving tags to help avoid conflicts
   :revision-id 1
   :format mt/edn})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn create-tag
  "Creates the tag saving it as a revision in metadata db. Returns the concept id and revision id of
  the saved tag."
  [context tag-json-str]
  (let [user-id (context->user-id context)
        tag (-> (tv/create-tag-json->tag tag-json-str)
                (assoc :originator-id user-id))]
    ;; Check if the tag already exists
    (if-let [concept-id (mdb/get-concept-id context :tag "CMR" (:tag-key tag) false)]

      ;; The tag exists. Check if its latest revision is a tombstone
      (let [concept (mdb/get-latest-concept context concept-id false)]
        (if (:deleted concept)
          ;; The tag exists but was previously deleted.
          (mdb/save-concept
            context
            (-> concept
                (assoc :metadata (pr-str tag)
                       :deleted false
                       :user-id user-id)
                (dissoc :revision-date)
                (update-in [:revision-id] inc)))

          ;; The tag exists and was not deleted. Reject this.
          (errors/throw-service-error :conflict (msg/tag-already-exists tag concept-id))))

      ;; The tag doesn't exist
      (mdb/save-concept context (tag->new-concept tag)))))

(defn- fetch-tag-concept
  "Fetches the latest version of a tag concept by tag-key"
  [context tag-key]
  (if-let [concept (mdb/find-latest-concept context
                                            {:native-id tag-key
                                             :latest true}
                                            :tag)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (msg/tag-deleted tag-key))
      concept)
    (errors/throw-service-error :not-found (msg/tag-does-not-exist tag-key))))

(defn get-tag
  "Retrieves a tag with the given concept id."
  [context tag-key]
  (edn/read-string (:metadata (fetch-tag-concept context tag-key))))

(defn update-tag
  "Updates an existing tag with the given concept id"
  [context tag-key tag-json-str]
  (let [updated-tag (tv/update-tag-json->tag tag-json-str)
        existing-concept (fetch-tag-concept context tag-key)
        existing-tag (edn/read-string (:metadata existing-concept))]
    (tv/validate-update-tag existing-tag updated-tag)
    ;; The updated tag won't change the originator of the existing tag
    (let [updated-tag (assoc updated-tag :originator-id (:originator-id existing-tag))]
      (mdb/save-concept
        context
        (-> existing-concept
            (assoc :metadata (pr-str updated-tag)
                   :user-id (context->user-id context))
            (dissoc :revision-date :transaction-id)
            (update-in [:revision-id] inc))))))

(defn delete-tag
  "Deletes a tag with the given concept id"
  [context tag-key]
  (let [existing-concept (fetch-tag-concept context tag-key)]
    (mdb/save-concept
      context
      (-> existing-concept
          ;; Remove fields not allowed when creating a tombstone.
          (dissoc :metadata :format :provider-id :native-id :transaction-id)
          (assoc :deleted true
                 :user-id (context->user-id context))
          (dissoc :revision-date)
          (update-in [:revision-id] inc)))))

(defn- save-concept-in-mdb
  "Save the given concept in metadata-db using the given embedded metadata-db context."
  [mdb-context concept]
  (let [{:keys [concept-id revision-id]} (mdb-cs/save-concept-revision mdb-context concept)]
    {:concept-id concept-id, :revision-id revision-id}))

(defn- delete-tag-association
  "Delete the tag association with the given native-id using the given embedded metadata-db context."
  [mdb-context native-id]
  (let [existing-concept (first (mdb-ss/find-concepts mdb-context
                                                      {:concept-type :tag-association
                                                       :native-id native-id
                                                       :exclude-metadata true
                                                       :latest true}))
        concept-id (:concept-id existing-concept)]
    (if concept-id
      (if (:deleted existing-concept)
        {:message {:warnings [(format "Tag association [%s] is already deleted." concept-id)]}}
        (let [concept {:concept-type :tag-association
                       :concept-id concept-id
                       :user-id (context->user-id mdb-context)
                       :deleted true}]
          (save-concept-in-mdb mdb-context concept)))
      {:message {:warnings [(msg/delete-tag-association-not-found native-id)]}})))

(defn- tag-association->native-id
  "Returns the native id of the given tag association."
  [tag-association]
  (let [{coll-concept-id :concept-id
         coll-revision-id :revision-id
         tag-key :tag-key} tag-association
        native-id (str tag-key native-id-separator-character coll-concept-id)]
    (if coll-revision-id
      (str native-id native-id-separator-character coll-revision-id)
      native-id)))

(defn- tag-association->concept-map
  "Returns the concept map for inserting into metadata-db for the given tag association."
  [tag-association]
  (let [{:keys [tag-key originator-id native-id user-id data errors]
         coll-concept-id :concept-id
         coll-revision-id :revision-id} tag-association]
    {:concept-type :tag-association
     :native-id native-id
     :user-id user-id
     :format mt/edn
     :metadata (pr-str
                 (util/remove-nil-keys
                   {:tag-key tag-key
                    :originator-id originator-id
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id
                    :data data}))
     :extra-fields {:tag-key tag-key
                    :associated-concept-id coll-concept-id
                    :associated-revision-id coll-revision-id}}))

(defn- update-tag-association
  "Based on the input operation type (:insert or :delete), insert or delete the given tag
  association using the embedded metadata-db, returns the tag association result in the format of
  {tag_association: {concept_id: TA1-CMR, revision_id: 1},
  tagged_item: {concept_id: C5-PROV1 revision_id: 2}}
  or
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  tagged_item: {concept_id: C6-PROV1}}."
  [mdb-context tag-association operation]
  ;; save each tag-association if there is no errors on it, otherwise returns the errors.
  (let [{coll-concept-id :concept-id
         coll-revision-id :revision-id
         errors :errors} tag-association
        native-id (tag-association->native-id tag-association)
        tag-association (-> tag-association
                            (assoc :native-id native-id)
                            (assoc :user-id (context->user-id mdb-context)))
        tagged-item (util/remove-nil-keys
                     {:concept-id coll-concept-id :revision-id coll-revision-id})]
    (if (seq errors)
      {:errors errors :tagged-item tagged-item}
      (try
        (let [{:keys [concept-id revision-id message]} ;; only delete-tag-association could potentially return message
              (if (= :insert operation)
                (save-concept-in-mdb mdb-context (tag-association->concept-map tag-association))
                (delete-tag-association mdb-context native-id))]
          (if (some? message)
            (merge {:tagged-item tagged-item} message)
            {:tag-association {:concept-id concept-id :revision-id revision-id}
             :tagged-item tagged-item}))
        (catch clojure.lang.ExceptionInfo e ; Report a specific error in the case of a conflict, otherwise throw error
          (if (= :conflict (:type (ex-data e)))
            {:errors (format association-conflict-error-message
                        (if (= :insert operation) "associate" "dissociate")
                        (:tag-key tag-association)
                        coll-concept-id
                        (if (= :insert operation) "association" "dissociation"))
             :tagged-item tagged-item}
            (throw e)))))))

(defn- update-tag-associations
  "Based on the input operation type (:insert or :delete), insert or delete tag associations,
  returns the tag association result in the format of
  [{tag_association: {concept_id: TA1-CMR, revision_id: 1},
  tagged_item: {concept_id: C5-PROV1 revision_id: 2}},
  {errors: [Collection [C6-PROV1] does not exist or is not visible.],
  tagged_item: {concept_id: C6-PROV1}}]."
  [context tag-concept tag-associations operation]
  (let [existing-tag (edn/read-string (:metadata tag-concept))
        {:keys [tag-key originator-id]} existing-tag
        mdb-context (assoc context :system
                           (get-in context [:system :embedded-systems :metadata-db]))
        [t1 result] (util/time-execution (util/fast-map
                                          (fn [association]
                                            (update-tag-association
                                             mdb-context (merge association {:tag-key tag-key
                                                                             :originator-id originator-id}) operation))
                                          tag-associations))]
    (debug "update-tag-associations:" t1)
    result))

(defn- update-tag-associations-with-query
  "Based on the input operation type (:insert or :delete), insert or delete tag associations
  identified by the json query. Throws service error if the tag with the given tag key is not found."
  [context tag-key json-query operation]
  (let [tag-concept (fetch-tag-concept context tag-key)
        query (-> (jp/parse-json-query :collection {} json-query)
                  (assoc :page-size :unlimited
                         :result-format :query-specified
                         :result-fields [:concept-id]
                         :skip-acls? false))
        coll-concept-ids (->> (qe/execute-query context query)
                              :items
                              (map :concept-id))]
    (update-tag-associations
      context tag-concept (map #(hash-map :concept-id %) coll-concept-ids) operation)))

(defn- link-tag-to-collections
  "Associate/Disassocate a tag to a list of collections in the tag associations json based on
  the given operation type. The ooperation type can be either :insert or :delete.
  Throws service error if the tag with the given tag key is not found."
  [context tag-key tag-associations-json operation-type]
  (let [tag-concept (fetch-tag-concept context tag-key)
        tag-associations (av/tag-associations-json->tag-associations tag-associations-json)
        [validation-time tag-associations] (util/time-execution
                                             (av/validate-tag-associations
                                               context operation-type tag-key tag-associations))]
    (debug "link-tag-to-collections validation-time:" validation-time)
    (update-tag-associations context tag-concept tag-associations operation-type)))

(defn associate-tag-to-collections
  "Associates a tag to the given list of tag associations in json."
  [context tag-key tag-associations-json]
  (link-tag-to-collections context tag-key tag-associations-json :insert))

(defn disassociate-tag-to-collections
  "Disassociates a tag from the given list of tag associations in json."
  [context tag-key tag-associations-json]
  (link-tag-to-collections context tag-key tag-associations-json :delete))

(defn associate-tag-by-query
  "Associates a tag with collections that are the result of a JSON query"
  [context tag-key json-query]
  (update-tag-associations-with-query context tag-key json-query :insert))

(defn disassociate-tag-by-query
  "Disassociates a tag from collections that are the result of a JSON query"
  [context tag-key json-query]
  (update-tag-associations-with-query context tag-key json-query :delete))

(defn search-for-tags
  "Returns the tag search result in JSON format."
  [context params]
  (let [results (:results (query-service/find-concepts-by-parameters
                            context :tag (assoc params :result-format :json)))]
    (-> (json/parse-string results true)
        util/map-keys->snake_case)))
