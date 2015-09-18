(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
            [cmr.common.validations.core :as v]
            [cmr.common.concepts :as concepts]
            [cmr.search.services.tagging.tagging-service-messages :as msg]
            [cmr.search.services.json-parameters.conversion :as jp]
            [cmr.search.services.query-execution :as qe]
            [cmr.search.services.query-service :as query-service]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.set :as set]))

(def ^:private native-id-separator-character
  "This is the separate character used when creating the native id for a tag. It is the ASCII
  character called group separator. This will not be allowed in the namespace or value of a tag."
  (char 29))

(defn- tag->native-id
  "Returns the native id to use for a tag."
  [tag]
  (str (:namespace tag) native-id-separator-character (:value tag)))

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
   :native-id (tag->native-id tag)
   :metadata (pr-str tag)
   :user-id (:originator-id tag)
   ;; The first version of a tag should always be revision id 1. We always specify a revision id
   ;; when saving tags to help avoid conflicts
   :revision-id 1
   :format mt/edn})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations

(defn- should-not-contain-native-id-separator-character
  "Validates the value does not contain the native id separator character."
  [field-path ^String value]
  (when (.contains value (str native-id-separator-character))
    {field-path [msg/field-may-not-contain-separator]}))

(def ^:private tag-validations
  "Service level validations for tags."
  {:namespace should-not-contain-native-id-separator-character
   :value should-not-contain-native-id-separator-character})

(defn- validate-create-tag
  "Validates a tag for creation"
  [tag]
  (v/validate! tag-validations tag))

(defn- field-cannot-be-changed
  "Validation that a field in a tag has not been modified. Accepts optional nil-allowed? parameter
  which indicates the validation should be skipped if the new value is nil."
  ([field]
   (field-cannot-be-changed field false))
  ([field nil-allowed?]
   (fn [field-path tag]
     (let [existing-value (get-in tag [:existing field])
           new-value (get tag field)
           ;; if nil is allowed and the new value is nil we skip validation.
           skip-validation? (and nil-allowed? (nil? new-value))]
       (when (and (not skip-validation?)
                  (not= existing-value new-value))
         {(conj field-path field)
          [(msg/cannot-change-field-value existing-value new-value)]})))))

(def ^:private update-tag-validations
  "Service level validations when updating a tag."
  [tag-validations
   (field-cannot-be-changed :namespace)
   (field-cannot-be-changed :value)
   ;; Originator id cannot change but we allow it if they don't specify a value.
   (field-cannot-be-changed :originator-id true)])

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
  (validate-create-tag tag)
  (let [user-id (context->user-id context)
        tag (assoc tag
                   :originator-id user-id
                   :associated-concept-ids #{})]
    ;; Check if the tag already exists
    (if-let [concept-id (mdb/get-concept-id context :tag "CMR" (tag->native-id tag) false)]

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
            (dissoc :revision-date)
            (update-in [:revision-id] inc))))))

(defn delete-tag
  "Deletes a tag with the given concept id"
  [context concept-id]
  (let [existing-concept (fetch-tag-concept context concept-id)]
    (mdb/save-concept
      context
      (-> existing-concept
          ;; Remove fields not allowed when creating a tombstone.
          (dissoc :metadata :format :provider-id :native-id)
          (assoc :deleted true
                 :user-id (context->user-id context))
          (dissoc :revision-date)
          (update-in [:revision-id] inc)))))

(defn- update-tag-associations-with-query
  "Modifies a tags associations. Finds collections using the query and then passes the existing
  associated collection ids and the ones found from the query to the function. Sets the collection
  ids as the result of the function."
  [context concept-id json-query update-fn]
  (let [query (-> (jp/parse-json-query :collection {} json-query)
                  (assoc :page-size :unlimited
                         :result-format :query-specified
                         :fields [:concept-id]
                         :skip-acls? false))
        concept-id-set (->> (qe/execute-query context query)
                            :items
                            (map :concept-id)
                            set)
        existing-concept (fetch-tag-concept context concept-id)
        existing-tag (edn/read-string (:metadata existing-concept))
        updated-tag (update-in existing-tag [:associated-concept-ids]
                               #(update-fn % concept-id-set))]
    (mdb/save-concept
      context
      (-> existing-concept
          (assoc :metadata (pr-str updated-tag)
                 :user-id (context->user-id context))
          (dissoc :revision-date)
          (update-in [:revision-id] inc)))))

(defn associate-tag-by-query
  "Associates a tag with collections that are the result of a JSON query"
  [context concept-id json-query]
  (update-tag-associations-with-query context concept-id json-query set/union))

(defn disassociate-tag-by-query
  "Disassociates a tag with collections that are the result of a JSON query"
  [context concept-id json-query]
  (update-tag-associations-with-query context concept-id json-query set/difference))

(defn search-for-tags
  "Searches for tags with the given result formats. Returns the results as a string."
  [context params]
  (:results (query-service/find-concepts-by-parameters
              context :tag (assoc params :result-format :json))))

(comment

  (def context {:system (get-in user/system [:apps :search])})

  (search-for-tags context {})

  )



