(ns cmr.indexer.data.concepts.variable
  "Contains functions to parse and convert variable concepts"
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :as util]
   [cmr.indexer.data.elasticsearch :as es]))

(defmethod es/parsed-concept->elastic-doc :variable
  [context concept parsed-concept]
  (let [{:keys [concept-id deleted]} concept
        {:keys [variable-name description originator-id]} parsed-concept]
    (if deleted
      ;; This is only called by re-indexing (bulk indexing)
      ;; Regular deleted variables would have gone through the index-service/delete-concept path.
      {:concept-id concept-id
       :deleted deleted}
      {:concept-id concept-id
       :variable-name.lowercase (string/lower-case variable-name)
       :description description
       :originator-id.lowercase  (util/safe-lowercase originator-id)})))

(defn variable-association->elastic-doc
  "Converts the variable association into the portion going in the collection elastic document."
  [variable-association]
  (let [{:keys [variable-name originator-id data]} variable-association]
    {:variable-name.lowercase (string/lower-case variable-name)
     :originator-id.lowercase  (util/safe-lowercase originator-id)
     :variable-value.lowercase (when (string? data)
                                 (string/lower-case data))}))
