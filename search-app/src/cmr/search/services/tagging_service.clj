(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
            [cmr.common.validations.core :as v]
            [cmr.search.services.tagging-service-messages :as msg]
            [clojure.string :as str]
            [clojure.edn :as edn]))

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
  "Validation that a field in a tag has not bee modified. Accepts optional nil-allowed? parameter
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

  ;; Validate that the tag doesn't yet exist
  (when-let [concept-id (mdb/get-concept-id context :tag "CMR" (tag->native-id tag) false)]
    ;; TODO check if the concept is deleted. We should allow this if it's a tombstone.
    (errors/throw-service-error
      :conflict (msg/tag-already-exists tag concept-id)))

  (let [user-id (context->user-id context)]
    (mdb/save-concept context (tag->new-concept (assoc tag :originator-id user-id)))))

(defn get-tag
  "Retrieves a tag with the given concept id."
  [context concept-id]
  (let [concept (mdb/get-latest-concept context concept-id)]
    ;; TODO check if it's deleted. Throw service error :not-found if deleted but error message
    ;; should be deleted
    (edn/read-string (:metadata concept))))

(defn update-tag
  "Updates an existing tag with the given concept id"
  [context concept-id tag]
  (let [existing-concept (mdb/get-latest-concept context concept-id)]
    ;; TODO error if trying to update a deleted tag (it doesn't exist)

    (let [existing-tag (edn/read-string (:metadata existing-concept))]
      (validate-update-tag existing-tag tag)
      (mdb/save-concept
        context
        (-> existing-concept
            (assoc :metadata (pr-str (assoc tag :originator-id (:originator-id existing-tag)))
                   :user-id (context->user-id context))
            (dissoc :revision-date)
            (update-in [:revision-id] inc))))))


