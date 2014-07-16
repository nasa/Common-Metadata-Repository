(ns cmr.search.results-handlers.atom-links-results-handler
  "Handles the ATOM links results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [clojure.data.xml :as x]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clj-time.core :as time]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :atom-links]
  [concept-type result-format]
  ["concept-id"
   "atom-links"])

(defmethod elastic-results/elastic-result->query-result-item :atom-links
  [context query elastic-result]
  (let [{concept-id :_id
         {atom-links :atom-links} :fields} elastic-result
        atom-links (map #(json/decode % true) atom-links)]
    [concept-id atom-links]))

