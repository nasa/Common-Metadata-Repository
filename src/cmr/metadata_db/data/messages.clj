(ns cmr.metadata-db.data.messages
  (:require [clojure.string :as string]))

(def missing-concept-id-msg
  "Concept with concept-type %s provider-id %s native-id %s does not exist.")