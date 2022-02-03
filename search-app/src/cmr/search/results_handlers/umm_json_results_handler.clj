(ns cmr.search.results-handlers.umm-json-results-handler
  "Handles Collection umm-json results format and related functions"
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.search.results-handlers.umm-json-results-helper :as results-helper]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]))

(defn- collection-elastic-result->meta
  "Returns a map of the meta fields for the given collection elastic result."
  [elastic-result]
  (results-helper/elastic-result->meta :collection elastic-result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collection UMM JSON

(defn- get-granule-count-for-item
  "Get the granule count from granule-counts-map using the concept-id in the item."
  [item granule-counts-map]
  (get granule-counts-map (get-in item [:meta :concept-id]) 0))

(defn- add-granule-count-to-items
  "Add the granule-count to the :meta part in each item in items using the granule-counts-map."
  [items granule-counts-map]
  (map #(assoc-in % [:meta :granule-count] (get-granule-count-for-item % granule-counts-map))
       items))

(defmethod gcrf/query-results->concept-ids :umm-json-results
  [results]
  (->> results
       :items
       (map :meta)
       (map :concept-id)))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :umm-json-results]
  [concept-type query]
  results-helper/meta-fields)

(defmethod elastic-results/elastic-result->query-result-item [:collection :umm-json-results]
  [context query elastic-result]
  (let [{entry-title :entry-title
         entry-id :entry-id
         short-name :short-name
         version-id :version-id} (:_source elastic-result)]
    {:meta (collection-elastic-result->meta elastic-result)
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defmethod results-helper/elastic-result+metadata->umm-json-item :collection
  [concept-type elastic-result metadata]
  {:meta (collection-elastic-result->meta elastic-result)
   :umm (json/decode metadata)})

(defmethod elastic-results/elastic-results->query-results [:collection :umm-json-results]
  [context query elastic-results]
  (results-helper/query-elastic-results->query-results context :collection query elastic-results))

(defmethod qs/search-results->response [:collection :umm-json-results]
  [context query results]
  (let [granule-counts-map (:granule-counts-map results)
        items (:items results)
        results (assoc results :items (if (some #{:granule-counts} (:result-features query))
                                        (add-granule-count-to-items items granule-counts-map)
                                        items))]
    (json/generate-string (select-keys results [:hits :took :items]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collection Legacy UMM JSON

(defmethod gcrf/query-results->concept-ids :legacy-umm-json
  [results]
  (->> results
       :items
       (map :meta)
       (map :concept-id)))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :legacy-umm-json]
  [concept-type query]
  (concat
   results-helper/meta-fields
   ["entry-title"
    "entry-id"
    "short-name"
    "version-id"]))

(defmethod elastic-results/elastic-result->query-result-item [:collection :legacy-umm-json]
  [context query elastic-result]
  (let [{entry-title :entry-title
         entry-id :entry-id
         short-name :short-name
         version-id :version-id} (:_source elastic-result)]
    {:meta (collection-elastic-result->meta elastic-result)
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defmethod qs/search-results->response [:collection :legacy-umm-json]
  [context query results]
  (let [granule-counts-map (:granule-counts-map results)
        items (:items results)
        results (assoc results :items (if (some #{:granule-counts} (:result-features query))
                                        (add-granule-count-to-items items granule-counts-map)
                                        items))]
    (json/generate-string (select-keys results [:hits :took :items]))))
