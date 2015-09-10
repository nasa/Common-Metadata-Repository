(ns cmr.search.services.tagging-service
  "Provides functions for storing and manipulating tags"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]))

(def ^:private native-id-separator-character
  "This is the separate character used when creating the native id for a tag. It is the ASCII
  character called group separator. This will not be allowed in the namespace or value of a tag."
  (char 29))

(defn- tag->native-id
  "Returns the native id to use for a tag."
  [tag]
  (str (:namespace tag) native-id-separator-character (:value tag)))

(defn- tag->concept
  "Converts a tag into a concept that can be persisted in metadata db."
  [tag]
  {:concept-type :tag
   :native-id (tag->native-id tag)
   :metadata (pr-str tag)
   :format mt/edn})

(defn create-tag
  "TODO
  Returns concept id and revision id of saved tag"
  [context tag]
  ;; TODO what validations do we do on a tag at this level?
  ;; TODO set originator id based on the user of the token in the context
  (mdb/save-concept context (tag->concept tag)))


(comment
  (def context {:system (get-in user/system [:apps :search])})
  (def tag {:namespace "ns"
            :value "foo"})


  (mdb/save-concept context (tag->concept tag))


  )