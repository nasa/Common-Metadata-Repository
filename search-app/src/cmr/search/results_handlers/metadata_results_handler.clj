(ns cmr.search.results-handlers.metadata-results-handler
  "Handles search results with metadata including ECHO10 and DIF formats."
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.common-app.services.search :as qs]
            [cmr.search.services.query-execution :as qe]
            [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
            [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
            [cmr.search.services.query-execution.tags-results-feature :as trf]
            [cmr.common-app.services.search.results-model :as results]
            [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.util :as u]
            [cmr.common.log :refer (debug info warn error)]))

(def result-formats
  "Supported search result formats by concept-types"
  {:granule [:echo10 :iso19115 :iso-smap :native]
   :collection [:echo10 :dif :dif10 :iso19115 :iso-smap :native]})

;; define functions to return fields for collection
(doseq [format (:collection result-formats)]
  (defmethod elastic-search-index/concept-type+result-format->fields [:collection format]
    [concept-type query]
    (let [fields ["metadata-format" "revision-id"]]
      (if (contains? (set (:result-features query)) :tags)
        (conj fields trf/stored-tags-field)
        fields))))

;; define functions to return fields for granule
(doseq [format (:granule result-formats)]
  (defmethod elastic-search-index/concept-type+result-format->fields [:granule format]
    [concept-type query]
    ["metadata-format"]))

(def concept-type->name-key
  "A map of the concept type to the key to use to extract the reference name field."
  {:collection :entry-title
   :granule :granule-ur})

(defn- elastic-result->query-result-item
  "Returns the query result item for the given elastic result"
  [concept-type elastic-result]
  {:concept-id (:_id elastic-result)
   :revision-id (elastic-results/get-revision-id-from-elastic-result concept-type elastic-result)
   :tags (when (= :collection concept-type)
           (trf/collection-elastic-result->tags elastic-result))})

(defn- add-tags
  "Returns the result items with tags added by matching with the concept tags map which is a map of
  [concept-id revision-id] to tags associated with the concept."
  [items concept-tags-map]
  (let [assoc-tag (fn [it]
                    (if-let [tags (concept-tags-map [(:concept-id it) (:revision-id it)])]
                      (assoc it :tags tags)
                      it))]
    (map assoc-tag items)))

(defn- elastic-results->query-metadata-results
  "A helper for converting elastic results into metadata results."
  [context query elastic-results]
  (let [{:keys [concept-type result-format result-features]} query
        hits (get-in elastic-results [:hits :total])
        scroll-id (:_scroll_id elastic-results)
        elastic-matches (get-in elastic-results [:hits :hits])
        result-items (mapv #(elastic-result->query-result-item concept-type %) elastic-matches)
        tuples (mapv #(vector (:concept-id %) (:revision-id %)) result-items)
        [req-time items] (u/time-execution
                          (metadata-cache/get-formatted-concept-revisions
                           context concept-type tuples result-format))
        ;; add tags to result items if necessary
        items (if (contains? (set result-features) :tags)
                (let [concept-tags-map (into {}
                                             (mapv #(hash-map [(:concept-id %) (:revision-id %)] (:tags %))
                                                   result-items))]
                  (add-tags items concept-tags-map))
                items)]
    (debug "Transformer metadata request time was" req-time "ms.")
    (results/map->Results {:hits hits 
                           :items items 
                           :result-format result-format
                           :scroll-id scroll-id})))


;; Define transormations methods from query results to concept-ids
(doseq [format [:echo10 :dif :dif10 :native]]
  (defmethod gcrf/query-results->concept-ids format
    [results]
    (->> results
         :items
         (map :concept-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search results handling

(defn- tags->result-string
  "Returns the list of result strings for the given tags."
  [tags]
  (when (seq tags)
    [(cx/remove-xml-processing-instructions
      (x/emit-str
       (x/element :tags {}
                  (for [[tag-key {:keys [data]}] tags]
                    (x/element :tag {}
                               (x/element :tagKey {} tag-key)
                               (when data
                                 (x/element :data {} (json/generate-string data))))))))]))

(defmulti metadata-item->result-string
  "Converts a search result + metadata into a string containing a single result for the metadata format."
  (fn [concept-type echo-compatible? results metadata-item]
    [concept-type echo-compatible?]))

;; Normal CMR Search Response handling

(defmethod metadata-item->result-string [:granule false]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [concept-id revision-id format metadata]} metadata-item
        collection-concept-id (get-in metadata-item [:extra-fields :parent-collection-id])]
    ["<result concept-id=\""
     concept-id
     "\" collection-concept-id=\""
     collection-concept-id
     "\" revision-id=\""
     revision-id
     "\" format=\""
     format
     "\">"
     metadata
     "</result>"]))

(defmethod metadata-item->result-string [:collection false]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [has-granules-map granule-counts-map]} results
        {:keys [concept-id revision-id format metadata tags]} metadata-item
        granule-count (get granule-counts-map concept-id 0)
        attribs (concat [[:concept-id concept-id]
                         [:revision-id revision-id]
                         [:format format]]
                        (when has-granules-map
                          [[:has-granules (or
                                            (< 0 granule-count)
                                            (get has-granules-map concept-id false))]])
                        (when granule-counts-map
                          [[:granule-count granule-count]]))
        attrib-strs (for [[k v] attribs]
                      (str " " (name k) "=\"" v "\""))]
    (concat ["<result"]
            attrib-strs
            [">" metadata]
            (tags->result-string tags)
            ["</result>"])))

;; ECHO Compatible Response Handling

(defmethod metadata-item->result-string [:granule true]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [concept-id metadata]} metadata-item
        collection-concept-id (get-in metadata-item [:extra-fields :parent-collection-id])]
    ["<result echo_granule_id=\""
     concept-id
     "\" echo_dataset_id=\""
     collection-concept-id
     "\">"
     metadata
     "</result>"]))

(defmethod metadata-item->result-string [:collection true]
  [concept-type echo-compatible? results metadata-item]
  (let [{:keys [concept-id metadata tags]} metadata-item]
    (concat ["<result echo_dataset_id=\"" concept-id "\">"
             metadata]
            (tags->result-string tags)
            ["</result>"])))

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


(doseq [concept-type [:collection :granule]
        metadata-format (distinct (flatten (vals result-formats)))]
  ;; define transformations from elastic results to query results for each format
  (defmethod elastic-results/elastic-results->query-results [concept-type metadata-format]
    [context query elastic-results]
    (elastic-results->query-metadata-results context query elastic-results))

  ;; define tranformations from search results to response for each format
  (defmethod qs/search-results->response [concept-type metadata-format]
    [context query results]
    (search-results->metadata-response context query results)))
