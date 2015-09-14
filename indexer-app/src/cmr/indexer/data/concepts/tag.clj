(ns cmr.indexer.data.concepts.tag
  "Contains functions to parse and convert tag concepts"
  (:require [clojure.string :as str]
            [cmr.common.log :refer (debug info warn error)]))

native-id
   tag-namespace
   value
   category
   description
   originator-id
   revision-date
   associated-concept-ids

(defmethod es/concept->elastic-doc :tag
  [context concept parsed-concept]
  (let [{:keys [concept-id]} concept
        {:keys [namespace value category description originator-id associated-concept-ids]}
        parsed-concept]
    {:concept-id concept-id
     :namespace namespace
     :namespace.lowercase (str/lower-case namespace)
     :value value
     :value.lowercase (str/lower-case value)
     :category category
     :category.lowercase (str/lower-case category)
     :description description
     :originator-id.lowercase  (str/lower-case originator-id)
     ;; TODO associated-concept-ids
     }))



