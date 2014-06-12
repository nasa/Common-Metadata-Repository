(ns cmr.search.data.elastic-results-to-query-results
  "Contains function to covert elasticsearch results to query reference results."
  (:require [cmr.search.models.results :as results]))

(defn location-root
  "Returns the url root for reference location"
  [context]
  (let [{:keys [protocol host port relative-root-url]} (get-in context [:system :search-pulblic-conf])
        port (if (empty? relative-root-url) port (format "%s%s" port relative-root-url))]
    (format "%s://%s:%s/concepts/" protocol host port)))

(defn- elastic-results->query-results-with-name-key
  "Converts the Elasticsearch results into the results expected from execute-query
  based on the given name-key. Returns the query reference results"
  [context elastic-results name-key]
  (let [hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        refs (map (fn [match]
                    (let [{concept-id :_id
                           revision-id :_version
                           {[name-value] name-key} :fields} match]
                      (results/map->Reference
                        {:concept-id concept-id
                         :revision-id revision-id
                         :location (format "%s%s" (location-root context) concept-id)
                         :name name-value})))
                  elastic-matches)]
    (results/map->Results {:hits hits :references refs})))

(defmulti elastic-results->query-results
  "Convert elasticsearch results to query references"
  (fn [context concept-type elastic-results]
    concept-type))

(defmethod elastic-results->query-results :collection
  [context concept-type elastic-results]
  (elastic-results->query-results-with-name-key context elastic-results :entry-title))

(defmethod elastic-results->query-results :granule
  [context concept-type elastic-results]
  (elastic-results->query-results-with-name-key context elastic-results :granule-ur))
