(ns cmr.search.results-handlers.concept-map-handler
   "Handles the concept map results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-execution.facets-results-feature :as frf]
            [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
            [cmr.search.services.query-service :as qs]
            [cmr.search.services.transformer :as t]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [clojure.string :as str]
            [cmr.search.services.url-helper :as url]
            [cmr.search.results-handlers.atom-results-handler :as atom]
            [cmr.search.results-handlers.atom-spatial-results-handler :as atom-spatial]
            [cmr.common.util :as util]
            [camel-snake-kebab.core :as csk]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :concept-map]
  [concept-type query]
  ["concept-id"])

(defn- results->concept-maps
  "Converts search results into a list of concept maps."
  [context results]
  (let [tuples (map #(vector (:concept-id %) (:_version %)) (get-in results [:hits :hits]))]
    (cmr.common.dev.capture-reveal/capture results)
    (t/get-formatted-concept-revisions context tuples :native false)))

(defmethod qs/search-results->response :concept-map
  [context query results]
  (json/generate-string (results->concept-maps context results)))

