(ns cmr.search.results-handlers.atom-links-results-handler
  "Handles the ATOM links results format and related functions.
  This is used by granule search in atom format where the atom links in the parent collection
  that are not browse type should be included in the granule atom links as inherited.
  This handles the retrieval of collection atom links by collection concept ids."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.search.services.query-execution :as qe]
            [cmr.search.models.query :as q]
            [clojure.data.xml :as x]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clj-time.core :as time]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :atom-links]
  [concept-type query]
  ["concept-id"
   "atom-links"])

(defmethod elastic-results/elastic-result->query-result-item [:collection :atom-links]
  [context query elastic-result]
  (let [{concept-id :_id
         {atom-links :atom-links} :fields} elastic-result
        atom-links (map #(json/decode % true) atom-links)]
    [concept-id atom-links]))

(defn find-collection-atom-links
  "Returns a mapping of collection-concept-ids and its atom links for the given collection-concept-ids"
  [context collection-concept-ids]
  (if (seq collection-concept-ids)
    (let [collection-links-query (q/query {:concept-type :collection
                                           :condition (q/string-conditions :concept-id collection-concept-ids true)
                                           :page-size :unlimited
                                           :result-format :atom-links})
          result (qe/execute-query context collection-links-query)]
      (into {} (:items result)))
    {}))
