(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
            [cmr.common.validations.core :as v]
            [clojure.string :as str]
            [cmr.search.services.tagging-service-messages :as msg]))

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

(defn- tag->concept
  "Converts a tag into a concept that can be persisted in metadata db."
  [context tag]
  (let [user-id (context->user-id context)]
    {:concept-type :tag
     :native-id (tag->native-id tag)
     :metadata (pr-str (assoc tag :originator-id user-id))
     :user-id user-id
     ;; The first version of a tag should always be revision id 1. We always specify a revision id
     ;; when saving tags to help avoid conflicts
     :revision-id 1
     :format mt/edn}))

(defn should-not-contain-native-id-separator-character
  "Validates the value does not contain the native id separator character."
  [field-path ^String value]
  (when (.contains value (str native-id-separator-character))
    {field-path [msg/field-may-not-contain-separator]}))

(def tag-validations
  "Service level validations for tags."
  {:namespace should-not-contain-native-id-separator-character
   :value should-not-contain-native-id-separator-character})

(defn create-tag
  "Creates the tag saving it as a revision in metadata db. Returns the concept id and revision id of
  the saved tag."
  [context tag]
  (v/validate! tag-validations tag)

  ;; Validate that the tag doesn't yet exist
  (when-let [concept-id (mdb/get-concept-id context :tag "CMR" (tag->native-id tag) false)]
    ;; TODO check if the concept is deleted. We should allow this if it's a tombstone.
    (errors/throw-service-error
      :conflict (msg/tag-already-exists tag concept-id)))

  (mdb/save-concept context (tag->concept context tag)))


