(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.validations.core :as v]
            [cmr.search.services.tagging.tag-association-validation :as tv]
            [cmr.common.concepts :as concepts]
            [cmr.search.services.tagging.tagging-service-messages :as msg]
            [cmr.search.services.json-parameters.conversion :as jp]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.search.services.query-service :as query-service]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(def ^:private native-id-separator-character
  "This is the separator character used when creating the native id for a tag."
  "/")

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
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
;; Validations

(def ^:private update-tag-validations
  "Service level validations when updating a tag."
  [(v/field-cannot-be-changed :tag-key)
   ;; Originator id cannot change but we allow it if they don't specify a value.
   (v/field-cannot-be-changed :originator-id true)])

(defn- validate-update-tag
  "Validates a tag update."
  [existing-tag updated-tag]
  (v/validate! update-tag-validations (assoc updated-tag :existing existing-tag)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn create-tag
  "Creates the tag saving it as a revision in metadata db. Returns the concept id and revision id of
  the saved tag."
  [context tag]
  (let [user-id (context->user-id context)
        tag (assoc tag
                   :originator-id user-id
                   :associated-concept-ids #{})]
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
  [context tag-key updated-tag]
  (let [existing-concept (fetch-tag-concept context tag-key)
        existing-tag (edn/read-string (:metadata existing-concept))]
    (validate-update-tag existing-tag updated-tag)
    ;; The updated tag won't change the originator of the existing tag or the associated collection ids.
    (let [updated-tag (assoc updated-tag
                             :originator-id (:originator-id existing-tag)
                             :associated-concept-ids (:associated-concept-ids existing-tag))]
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

(defn- delete-tag-association
  "Delete the tag association with the given native-id"
  [context native-id]
  (let [existing-concept (first (mdb/find-concepts context
                                                   {:native-id native-id
                                                    :exclude-metadata true
                                                    :latest true}
                                                   :tag-association))
        concept-id (:concept-id existing-concept)]
    (when (and concept-id (not (:deleted existing-concept)))
      (let [concept {:concept-type :tag-association
                     :concept-id concept-id
                     :user-id (context->user-id context)
                     :deleted true}
            {:keys [revision-id]} (mdb/save-concept context concept)]
        {:concept-id concept-id, :revision-id revision-id}))))

(defn- update-tag-association-to-collections
  "Based on the input operation type (:insert or :delete), insert or delete tag associations to
  the list of collections."
  [context tag-concept collections operation]
  (let [existing-tag (edn/read-string (:metadata tag-concept))
        {:keys [tag-key originator-id]} existing-tag]
    ;; save tag-association for each collection
    (remove nil?
            (for [coll collections
                  :let [coll-concept-id (:concept-id coll)
                        coll-revision-id (:revision-id coll)
                        data (:data coll)
                        native-id (str tag-key native-id-separator-character coll-concept-id)
                        native-id (if coll-revision-id
                                    (str native-id native-id-separator-character coll-revision-id)
                                    native-id)]]
              (if (= :insert operation)
                (mdb/save-concept context
                                  {:concept-type :tag-association
                                   :native-id native-id
                                   :user-id (context->user-id context)
                                   :format (mt/format->mime-type :edn)
                                   :metadata (pr-str
                                               (util/remove-nil-keys
                                                 {:tag-key tag-key
                                                  :originator-id originator-id
                                                  :associated-concept-id coll-concept-id
                                                  :associated-revision-id coll-revision-id
                                                  :data data}))
                                   :extra-fields {:tag-key tag-key
                                                  :associated-concept-id coll-concept-id
                                                  :associated-revision-id coll-revision-id}})
                (delete-tag-association context native-id))))))

(defn- update-tag-associations-with-query
  "Based on the input operation type (:insert or :delete), insert or delete tag associations
  identified by the json query."
  [context tag-key json-query operation]
  (let [query (-> (jp/parse-json-query :collection {} json-query)
                  (assoc :page-size :unlimited
                         :result-format :query-specified
                         :fields [:concept-id]
                         :skip-acls? false))
        coll-concept-ids (->> (qe/execute-query context query)
                              :items
                              (map :concept-id))
        tag-concept (fetch-tag-concept context tag-key)]
    (update-tag-association-to-collections
      context tag-concept (map #(hash-map :concept-id %) coll-concept-ids) operation)))

(defn- link-tag-to-collections
  "Associate/Disassocate a tag to a list of collections based on the given operation type.
  The ooperation type can be either :insert or :delete."
  [context tag-key collections-json operation-type]
  (let [tag-concept (fetch-tag-concept context tag-key)
        collections (tv/collections-json->collections collections-json)]
    (tv/validate-tag-association context tag-key collections)
    (update-tag-association-to-collections context tag-concept collections operation-type)))

(defn associate-tag-to-collections
  "Associates a tag to the given list of collections."
  [context tag-key collections-json]
  (link-tag-to-collections context tag-key collections-json :insert))

(defn disassociate-tag-to-collections
  "Associates a tag to the given list of collections."
  [context tag-key collections-json]
  (link-tag-to-collections context tag-key collections-json :delete))

(defn associate-tag-by-query
  "Associates a tag with collections that are the result of a JSON query"
  [context tag-key json-query]
  (update-tag-associations-with-query context tag-key json-query :insert))

(defn disassociate-tag-by-query
  "Disassociates a tag with collections that are the result of a JSON query"
  [context tag-key json-query]
  (update-tag-associations-with-query context tag-key json-query :delete))

(defn search-for-tags
  "Searches for tags with the given result formats. Returns the results as a string."
  [context params]
  (:results (query-service/find-concepts-by-parameters
              context :tag (assoc params :result-format :json))))
