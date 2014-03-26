(ns cmr.metadata-db.data.messages
  (:require [clojure.string :as string]))

(def missing-concept-id-msg
  "Concept with concept-type %s provider-id %s native-id %s does not exist.")

(def concept-does-not-exist-msg
  "Concept with concept-id %s does not exist.")

(def invalid-revision-id-msg
  "Expected revision-id of %s got %s")

(def invalid-revision-id-unknown-expected-msg
  "Invalid revison-id %s")

(def missing-concept-type-msg
  "Concept must include concept-type.")

(def missing-provider-id-msg
  "Concept must include provider-id.")

(def missing-native-id-msg
  "Concept must include native-id.")

(def invalid-concept-id-msg
  "concept-id for concept does not match provider-id or concept-type.")

(def concept-exists-with-differnt-id-msg
  "A concept with a differnt concept-id from %s already exists for concept-type %s provider-id %s and native-id %s")

(def maximum-save-attempts-exceeded-msg
  "Reached limit of attempts to save concept - giving up.")