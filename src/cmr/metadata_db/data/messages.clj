(ns cmr.metadata-db.data.messages
  (:require [clojure.string :as string]))

(def missing-concept-id-msg
  "Concept with concept-type %s provider-id %s native-id %s does not exist.")

(def invalid-version-id-msg
  "Expected revision-id of %s got %s")

(def missing-concept-type-msg
  "Concept must include concept-type.")

(def missing-provider-id-msg
  "Concept must include provider-id.")

(def missing-native-id-msg
  "Concept must include native-id.")