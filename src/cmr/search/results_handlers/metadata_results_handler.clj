(ns cmr.search.results-handlers.metadata-results-handler
  "Handles search results with metadata including ECHO10 and DIF formats."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.search.models.results :as results]
            [cmr.transmit.transformer :as t]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [clojure.string :as str]
            [cmr.umm.dif.collection :as dif-c]
            [cmr.common.util :as u]
            [cmr.common.log :refer (debug info warn error)]))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :echo10]
  [concept-type result-format]
  [])

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :echo10]
  [concept-type result-format]
  [])

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :dif]
  [concept-type result-format]
  [])

(def concept-type->name-key
  "A map of the concept type to the key to use to extract the reference name field."
  {:collection :entry-title
   :granule :granule-ur})

(defn- elastic-results->query-metadata-results
  "A helper for converting elastic results into metadata results."
  [context query elastic-results]
  (let [hits (get-in elastic-results [:hits :total])

        ;; TODO - big temporary hack here. All the revision ids indexed in workload were found to be 2 when metadata-db has 1.
        ;; See https://bugs.earthdata.nasa.gov/browse/CMR-609
        tuples (map #(vector (:_id %) 1 #_(:_version %))
                    (get-in elastic-results [:hits :hits]))
        [req-time tresults] (u/time-execution
                              (t/get-formatted-concept-revisions context tuples (:result-format query)))
        items (map #(select-keys % [:concept-id :revision-id :collection-concept-id :metadata]) tresults)]
    (debug "Transformer metadata request time was" req-time "ms.")
    (results/map->Results {:hits hits :items items})))

(defmethod elastic-results/elastic-results->query-results :echo10
  [context query elastic-results]
  (elastic-results->query-metadata-results context query elastic-results))

(defmethod elastic-results/elastic-results->query-results :dif
  [context query elastic-results]
  (elastic-results->query-metadata-results context query elastic-results))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search results handling

(defmulti metadata-item->result-string
  "Converts a search result + metadata into a string containing a single result for the metadata format."
  (fn [concept-type metadata-item]
    concept-type))

(defmethod metadata-item->result-string :granule
  [concept-type metadata-item]
  (let [{:keys [concept-id collection-concept-id revision-id metadata]} metadata-item]
    ["<result concept-id=\""
     concept-id
     "\" collection-concept-id=\""
     collection-concept-id
     "\" revision-id=\""
     revision-id
     "\">"
     (cx/remove-xml-processing-instructions metadata)
     "</result>"]))

(defmethod metadata-item->result-string :collection
  [concept-type metadata-item]
  (let [{:keys [concept-id revision-id metadata]} metadata-item]
    ["<result concept-id=\""
     concept-id
     "\" revision-id=\""
     revision-id
     "\">"
     (cx/remove-xml-processing-instructions metadata)
     "</result>"]))

(defn search-results->metadata-response
  [context query results]
  (let [{:keys [hits took items]} results
        {:keys [result-format pretty? concept-type]} query
        result-strings (apply concat (map (partial metadata-item->result-string concept-type) items))
        headers ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                 "<results><hits>"
                 hits
                 "</hits><took>"
                 took
                 "</took>"]
        footers ["</results>"]
        response (apply str (concat headers result-strings footers))]
    (if pretty?
      (let [parsed (x/parse-str response)
            ;; Fix for DIF emitting XML
            parsed (if (= :dif result-format)
                     (cx/update-elements-at-path
                       parsed [:result :DIF]
                       assoc :attrs dif-c/dif-header-attributes)
                     parsed)]
        (x/indent-str parsed))

      response)))

(defmethod qs/search-results->response :echo10
  [context query results]
  (search-results->metadata-response context query results))

(defmethod qs/search-results->response :dif
  [context query results]
  (search-results->metadata-response context query results))