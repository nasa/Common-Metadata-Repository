(ns cmr.search.results-handlers.metadata-results-handler
  "Handles search results with metadata including ECHO10 and DIF formats."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.search.services.query-execution :as qe]
            [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
            [cmr.search.services.query-execution.facets-results-feature :as frf]
            [cmr.search.models.results :as results]
            [cmr.search.services.transformer :as t]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [clojure.string :as str]
            [cmr.umm.dif.collection :as dif-c]
            [cmr.umm.iso-mends.collection]
            [cmr.umm.iso-mends.collection]
            [cmr.common.util :as u]
            [cmr.common.log :refer (debug info warn error)]))

(def result-formats
  "Supported search result formats by concept-types"
  {:granule [:echo10 :iso19115 :iso-smap :native]
   :collection [:echo10 :dif :dif10 :iso19115 :iso-smap :native]})

;; define functions to return fields for each concept type
(doseq [concept-type [:collection :granule]
        format (concept-type result-formats)]
  (defmethod elastic-search-index/concept-type+result-format->fields [concept-type format]
    [concept-type result-format]
    ["metadata-format"]))

(def concept-type->name-key
  "A map of the concept type to the key to use to extract the reference name field."
  {:collection :entry-title
   :granule :granule-ur})

(defn- elastic-results->query-metadata-results
  "A helper for converting elastic results into metadata results."
  [context query elastic-results]
  (let [hits (get-in elastic-results [:hits :total])
        tuples (map #(vector (:_id %) (:_version %)) (get-in elastic-results [:hits :hits]))
        [req-time tresults] (u/time-execution
                              (t/get-formatted-concept-revisions context tuples (:result-format query) false))
        items (map #(select-keys % qe/metadata-result-item-fields) tresults)]
    (debug "Transformer metadata request time was" req-time "ms.")
    (results/map->Results {:hits hits :items items :result-format (:result-format query)})))


;; Define transormations methods from query results to concept-ids
(doseq [format [:echo10 :dif :dif10]]
  (defmethod gcrf/query-results->concept-ids format
    [results]
    (->> results
         :items
         (map :concept-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search results handling

(defmulti metadata-item->result-string
  "Converts a search result + metadata into a string containing a single result for the metadata format."
  (fn [concept-type echo-compatible? results metadata-item]
    [concept-type echo-compatible?]))

;; Normal CMR Search Response handling

(defmethod metadata-item->result-string [:granule false]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [concept-id collection-concept-id revision-id format metadata]} metadata-item]
    ["<result concept-id=\""
     concept-id
     "\" collection-concept-id=\""
     collection-concept-id
     "\" revision-id=\""
     revision-id
     "\" format=\""
     format
     "\">"
     (cx/remove-xml-processing-instructions metadata)
     "</result>"]))

(defmethod metadata-item->result-string [:collection false]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [has-granules-map granule-counts-map]} results
        {:keys [concept-id revision-id format metadata]} metadata-item
        attribs (concat [[:concept-id concept-id]
                         [:revision-id revision-id]
                         [:format format]]
                        (when has-granules-map
                          [[:has-granules (get has-granules-map concept-id false)]])
                        (when granule-counts-map
                          [[:granule-count (get granule-counts-map concept-id 0)]]))
        attrib-strs (for [[k v] attribs]
                      (str " " (name k) "=\"" v "\""))]
    (concat ["<result"]
            attrib-strs
            [">" (cx/remove-xml-processing-instructions metadata) "</result>"])))

;; ECHO Compatible Response Handling

(defmethod metadata-item->result-string [:granule true]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [concept-id collection-concept-id metadata]} metadata-item]
    ["<result echo_granule_id=\""
     concept-id
     "\" echo_dataset_id=\""
     collection-concept-id
     "\">"
     (cx/remove-xml-processing-instructions metadata)
     "</result>"]))

(defmethod metadata-item->result-string [:collection true]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [concept-id metadata]} metadata-item]
    ["<result echo_dataset_id=\""
     concept-id
     "\">"
     (cx/remove-xml-processing-instructions metadata)
     "</result>"]))

(defn- facets->xml-string
  "Converts facets into an XML string."
  [facets]
  (if facets
    (cx/remove-xml-processing-instructions
      (x/emit-str (frf/facets->xml-element facets)))
    ""))

(defn search-results->metadata-response
  [context query results]
  (let [{:keys [hits took items facets]} results
        {:keys [result-format concept-type echo-compatible?]} query
        result-strings (apply concat (map (partial metadata-item->result-string
                                                   concept-type echo-compatible? results)
                                          items))
        headers (if echo-compatible?
                  ["<?xml version=\"1.0\" encoding=\"UTF-8\"?><results>"]
                  ["<?xml version=\"1.0\" encoding=\"UTF-8\"?><results><hits>"
                   hits "</hits><took>" took "</took>"])
        ;; Facet response is not in ECHO response.
        facets-strs (when-not echo-compatible? [(facets->xml-string facets)])
        footers ["</results>"]]
    (apply str (concat headers result-strings facets-strs footers))))


(doseq [format (distinct (flatten (vals result-formats)))]
  ;; define transformations from elastic results to query results for each format
  (defmethod elastic-results/elastic-results->query-results format
    [context query elastic-results]
    (elastic-results->query-metadata-results context query elastic-results))

  ;; define tranformations from search results to response for each format
  (defmethod qs/search-results->response format
    [context query results]
    (search-results->metadata-response context query results)))
