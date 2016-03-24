(ns cmr.indexer.data.concepts.tag
  "Contains functions to parse and convert tag concepts"
  (:require [clojure.string :as str]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.indexer.data.elasticsearch :as es]))

(defmethod es/concept->elastic-doc :tag
  [context concept parsed-concept]
  (let [{:keys [concept-id]} concept
        {:keys [tag-key description originator-id]}
        parsed-concept]
    {:concept-id concept-id
     :tag-key.lowercase (str/lower-case tag-key)
     :description description
     :originator-id.lowercase  (str/lower-case originator-id)}))

(defn tag-association->elastic-doc
  "Converts the tag association into the portion going in the collection elastic document."
  [tag-association]
  (let [{:keys [tag-key originator-id data]} tag-association]
    {:tag-key.lowercase (str/lower-case tag-key)
     :originator-id.lowercase  (str/lower-case originator-id)
     :tag-value.lowercase (when (string? data)
                            (str/lower-case data))}))