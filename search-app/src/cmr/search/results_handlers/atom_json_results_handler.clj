(ns cmr.search.results-handlers.atom-json-results-handler
  "Handles the JSON results format and related functions"
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-time.core :as time]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.atom-results-handler :as atom]
   [cmr.search.results-handlers.atom-spatial-results-handler :as atom-spatial]
   [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.search.services.url-helper :as url]))

(doseq [concept-type [:collection :granule]]
  (defmethod elastic-search-index/concept-type+result-format->fields [concept-type :json]
    [concept-type query]
    (elastic-search-index/concept-type+result-format->fields
     concept-type (assoc query :result-format :atom)))

  (defmethod elastic-results/elastic-results->query-results [concept-type :json]
    [context query elastic-results]
    (elastic-results/elastic-results->query-results
     context (assoc query :result-format :atom) elastic-results)))

(defmethod gcrf/query-results->concept-ids :json
  [results]
  (->> results
       :items
       (map :id)))

(defmulti atom-reference->json
  "Converts a search result atom reference into json"
  (fn [results concept-type reference]
    concept-type))

(defn- fix-map-for-echo-json
  "Convert the keys in a map to underscore form and converts values to strings (because
  that is how it is in ECHO json). The final result is filtered of keys whose values are empty."
  [input-map]
  (into {} (filter (fn [[k v]] (seq v))
                   (for [[k v] input-map] [(csk/->snake_case_string (name k)) (str v)]))))

(defmethod atom-reference->json :collection
  [results concept-type reference]
  (let [{:keys [has-granules-map granule-counts-map]} results
        {:keys [id score title short-name version-id summary updated dataset-id collection-data-type
                processing-level-id original-format data-center archive-center start-date end-date
                atom-links associated-difs online-access-flag browse-flag coordinate-system shapes
                orbit-parameters highlighted-summary-snippets tags organizations
                has-variables has-formats associations]} reference
        shape-result (atom-spatial/shapes->json shapes)
        granule-count (get granule-counts-map id 0)
        result (merge {:id id
                       :score score
                       :title title
                       :summary summary
                       :updated updated
                       :dataset_id dataset-id
                       :short_name short-name
                       :version_id version-id
                       :original_format original-format
                       :collection_data_type collection-data-type
                       :data_center data-center
                       :archive_center archive-center
                       :organizations organizations
                       :processing_level_id processing-level-id
                       :time_start start-date
                       :time_end end-date
                       :dif_ids associated-difs
                       :online_access_flag online-access-flag
                       :browse_flag browse-flag
                       :has_granules (when has-granules-map (or (< 0 granule-count)
                                                                (get has-granules-map id false)))
                       :granule_count (when granule-counts-map granule-count)
                       :has_variables has-variables
                       :has_formats has-formats
                       :links (seq (map atom/atom-link->attribute-map atom-links))
                       :coordinate_system coordinate-system
                       :orbit_parameters (when orbit-parameters
                                           (fix-map-for-echo-json orbit-parameters))
                       :highlighted-summary-snippets highlighted-summary-snippets
                       :tags tags
                       :associations associations}
                      shape-result)]
    ;; remove entries with nil value
    (util/remove-nil-keys result)))

(defmethod atom-reference->json :granule
  [results concept-type reference]
  (let [{:keys [id title updated dataset-id producer-gran-id size original-format
                data-center start-date end-date atom-links online-access-flag browse-flag
                day-night cloud-cover coordinate-system shapes
                orbit orbit-calculated-spatial-domains]} reference
        shape-result (atom-spatial/shapes->json shapes)
        result (merge {:id id
                       :title title
                       :updated updated
                       :dataset_id dataset-id
                       :producer_granule_id producer-gran-id
                       :granule_size size
                       :original_format original-format
                       :data_center data-center
                       :time_start start-date
                       :time_end end-date
                       :links (seq (map atom/atom-link->attribute-map atom-links))
                       :online_access_flag online-access-flag
                       :browse_flag browse-flag
                       :day_night_flag day-night
                       :cloud_cover cloud-cover
                       :coordinate_system coordinate-system
                       :orbit (when orbit (fix-map-for-echo-json orbit))
                       :orbit_calculated_spatial_domains (seq (map
                                                                fix-map-for-echo-json
                                                                orbit-calculated-spatial-domains))}
                      shape-result)]
    ;; remove entries with nil value
    (util/remove-nil-keys result)))

(defmulti results->json
  "Converts search results into json"
  (fn [context echo-compatible? include-facets? concept-type results]
    (and echo-compatible? include-facets?)))

(defmethod results->json true
  [context echo-compatible? include-facets? concept-type results]
  (let [{:keys [facets]} results]
    (frf/facets->echo-json facets)))

(defmethod results->json false
  [context echo-compatible? include-facets? concept-type results]
  (let [{:keys [items facets]} results
        items (if (= :granule concept-type)
                (atom/append-collection-links context items)
                items)]
    {:feed
     (util/remove-nil-keys
       {:updated (str (time/now))
        :id (url/atom-request-url context concept-type :json)
        :title (atom/concept-type->atom-title concept-type)
        :entry (map (partial atom-reference->json results concept-type) items)
        :facets facets})}))

(defn- search-results->response
  [context query results]
  (let [{:keys [concept-type echo-compatible? result-features]} query
        include-facets? (boolean (some #{:facets} result-features))
        response-results (results->json
                           context echo-compatible? include-facets? concept-type results)]
    (json/generate-string response-results)))

(defn- single-result->response
  [context query results]
  (json/generate-string (atom-reference->json results
                                              (:concept-type query)
                                              (first (:items results)))))

(doseq [concept-type [:collection :granule]]
  (defmethod qs/search-results->response [concept-type :json]
    [context query results]
    (search-results->response context query results))

  (defmethod qs/single-result->response [concept-type :json]
    [context query results]
    (single-result->response  context query results)))
