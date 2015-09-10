(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
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
     :revision-id 1
     :format mt/edn}))

(defn create-tag
  "TODO
  Returns concept id and revision id of saved tag"
  [context tag]
  ;; TODO what validations do we do on a tag at this level?
  ;; - no group seperator in namespace or value
  ;; TODO put those validations right here (maybe)
  ;; Use validation framework to do it.

  ;; TODO does this namespace and value combination already exist in metadata db?

  (mdb/save-concept context (tag->concept context tag)))


(comment
  (def context {:system (get-in user/system [:apps :search])})
  (def tag {:namespace "ns"
            :value "foo"})


  (mdb/save-concept context (tag->concept tag))


  )