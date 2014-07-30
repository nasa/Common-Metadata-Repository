(ns cmr.search.results-handlers.all-collections-results-handler
  "Handles the all collections results format and related functions.
  This is used by provider holdings retrieval to get all collections
  for a list of providers."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :core-fields]
  [concept-type result-format]
  ["entry-title"
   "provider-id"])

(defmethod elastic-results/elastic-result->query-result-item :core-fields
  [context query elastic-result]
  (let [{concept-id :_id
         {[entry-title] :entry-title
          [provider-id] :provider-id} :fields} elastic-result]
    {:concept-id concept-id
     :entry-title entry-title
     :provider-id provider-id}))
