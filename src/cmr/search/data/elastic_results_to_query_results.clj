(ns cmr.search.data.elastic-results-to-query-results
  "Contains function to covert elasticsearch results to query reference results."
  (:require [clojure.string :as s]
            [cmr.search.models.results :as results]
            [cmr.search.services.url-helper :as url]))

(defmulti elastic-result->query-result-reference
  "Converts the Elasticsearch result into the result expected from execute-query for the given format."
  (fn [context result-format name-key elastic-result]
    result-format))

(defmethod elastic-result->query-result-reference :csv
  [context result-format name-key elastic-result]
  (let [{concept-id :_id
         revision-id :_version
         {[granule-ur] :granule-ur
          [producer-gran-id] :producer-gran-id
          [start-date] :start-date
          [end-date] :end-date
          [downloadable-urls] :downloadable-urls
          [cloud-cover] :cloud-cover
          [day-night] :day-night
          [size] :size} :fields} elastic-result
        start-date (when start-date (s/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (s/replace (str end-date) #"\+0000" "Z"))]
    [granule-ur producer-gran-id start-date end-date downloadable-urls
     (str cloud-cover) day-night (str size)]))

(defmethod elastic-result->query-result-reference :atom
  [context result-format name-key elastic-result]
  (let [{concept-id :_id
         revision-id :_version
         {[granule-ur] :granule-ur
          [entry-title] :entry-title
          [producer-gran-id] :producer-gran-id
          [size] :size
          [original-format] :original-format
          [provider-id] :provider-id
          [start-date] :start-date
          [end-date] :end-date
          downloadable-urls :downloadable-urls
          browse-urls :browse-urls
          documentation-urls :documentation-urls
          metadata-urls :metadata-urls
          [downloadable] :downloadable
          [browsable] :browsable
          [day-night] :day-night
          [cloud-cover] :cloud-cover} :fields} elastic-result
        start-date (when start-date (s/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (s/replace (str end-date) #"\+0000" "Z"))]
    {:id concept-id
     :title granule-ur
     ;; TODO: last-updated is not indexed yet
     ; :updated last-updated
     :dataset-id entry-title
     :producer-gran-id producer-gran-id
     :size (str size)
     :original-format original-format
     :data-center provider-id
     :start-date start-date
     :end-date end-date
     :downloadable-urls downloadable-urls
     :browse-urls browse-urls
     :documentation-urls documentation-urls
     :metadata-urls metadata-urls
     ;; TODO spatial info goes here
     :online-access-flag downloadable
     :browse-flag browsable
     :day-night day-night
     :cloud-cover (str cloud-cover)}))

(defmethod elastic-result->query-result-reference :echo10
  [context result-format name-key elastic-result]
  (let [{concept-id :_id
         revision-id :_version
         {[collection-concept-id] :collection-concept-id} :fields} elastic-result]
    (results/map->Reference
      {:concept-id concept-id
       :revision-id revision-id
       :collection-concept-id collection-concept-id})))

(defmethod elastic-result->query-result-reference :default
  [context result-format name-key elastic-result]
  (let [{concept-id :_id
         revision-id :_version
         {[name-value] name-key} :fields} elastic-result]
    (results/map->Reference
      {:concept-id concept-id
       :revision-id revision-id
       :location (format "%s%s" (url/reference-root context) concept-id)
       :name name-value})))

(defn- elastic-results->query-results-with-name-key
  "Converts the Elasticsearch results into the results expected from execute-query
  based on the given name-key. Returns the query reference results"
  [context elastic-results result-format name-key]
  (let [hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        refs (map (partial elastic-result->query-result-reference context result-format name-key)
                  elastic-matches)]
    (results/map->Results {:hits hits :references refs})))

(defmulti elastic-results->query-results
  "Convert elasticsearch results to query references"
  (fn [context concept-type elastic-results result-format]
    concept-type))

(defmethod elastic-results->query-results :collection
  [context concept-type elastic-results result-format]
  (elastic-results->query-results-with-name-key context elastic-results result-format :entry-title))

(defmethod elastic-results->query-results :granule
  [context concept-type elastic-results result-format]
  (elastic-results->query-results-with-name-key context elastic-results result-format :granule-ur))
