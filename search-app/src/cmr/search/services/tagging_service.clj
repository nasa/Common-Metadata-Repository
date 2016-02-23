(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.validations.core :as v]
            [cmr.common.concepts :as concepts]
            [cmr.search.services.tagging.tagging-service-messages :as msg]
            [cmr.search.services.json-parameters.conversion :as jp]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.search.services.query-service :as query-service]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.set :as set]))

(def ^:private native-id-separator-character
  "This is the separate character used when creating the native id for a tag. It is the ASCII
  character called group separator. This will not be allowed in the namespace or value of a tag."
  (char 29))

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
  "Fetches the latest version of a tag concept by concept id"
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (or (not= :tag concept-type) (not= "CMR" provider-id))
      (errors/throw-service-error :bad-request (msg/bad-tag-concept-id concept-id))))

  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (msg/tag-deleted concept-id))
      concept)
    (errors/throw-service-error :not-found (msg/tag-does-not-exist concept-id))))

(defn get-tag
  "Retrieves a tag with the given concept id."
  [context concept-id]
  (edn/read-string (:metadata (fetch-tag-concept context concept-id))))

(defn update-tag
  "Updates an existing tag with the given concept id"
  [context concept-id updated-tag]
  (let [existing-concept (fetch-tag-concept context concept-id)
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
  [context concept-id]
  (let [existing-concept (fetch-tag-concept context concept-id)]
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
    (when-not concept-id
      (errors/throw-service-error
        :not-found (cmsg/invalid-native-id-msg :tag-association "CMR" native-id)))
    (when (:deleted existing-concept)
      (errors/throw-service-error
        :not-found (format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                           native-id concept-id)))
    (let [concept {:concept-type :tag-association
                   :concept-id concept-id
                   :deleted true}
          {:keys [revision-id]} (mdb/save-concept context concept)]
      {:concept-id concept-id, :revision-id revision-id})))

(defn- update-tag-associations-with-query
  "Based on the input operation type (:insert or :delete), insert or delete tag associations
  identified by the json query."
  [context concept-id json-query operation]
  (let [query (-> (jp/parse-json-query :collection {} json-query)
                  (assoc :page-size :unlimited
                         :result-format :query-specified
                         :fields [:concept-id]
                         :skip-acls? false))
        coll-concept-ids (->> (qe/execute-query context query)
                              :items
                              (map :concept-id))
        existing-concept (fetch-tag-concept context concept-id)
        tag-key (:native-id existing-concept)]
    ;; save tag-association for each collection concept-id
    (for [coll-concept-id coll-concept-ids
          :let [native-id (str tag-key native-id-separator-character coll-concept-id)]]
      (if (= :insert operation)
        (mdb/save-concept context {:concept-type :tag-association
                                   :native-id native-id
                                   :user-id (context->user-id context)
                                   :format (mt/format->mime-type :edn)
                                   :metadata (pr-str {:tag-key tag-key
                                                      :associated-concept-id coll-concept-id})
                                   :extra-fields {:associated-concept-id coll-concept-id}})
        (delete-tag-association context native-id)))))

(defn associate-tag-by-query
  "Associates a tag with collections that are the result of a JSON query"
  [context concept-id json-query]
  (update-tag-associations-with-query context concept-id json-query :insert))

(defn disassociate-tag-by-query
  "Disassociates a tag with collections that are the result of a JSON query"
  [context concept-id json-query]
  (update-tag-associations-with-query context concept-id json-query :delete))

(defn search-for-tags
  "Searches for tags with the given result formats. Returns the results as a string."
  [context params]
  (:results (query-service/find-concepts-by-parameters
              context :tag (assoc params :result-format :json))))



