(ns cmr.search.results-handlers.atom-json-results-handler
  "Handles the JSON results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
            [cmr.search.services.query-service :as qs]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [clojure.string :as str]
            [cmr.search.services.url-helper :as url]
            [cmr.search.results-handlers.atom-results-handler :as atom]
            [cmr.search.results-handlers.atom-spatial-results-handler :as atom-spatial]
            [cmr.common.util :as util]
            [camel-snake-kebab :as csk]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :json]
  [concept-type query]
  (elastic-search-index/concept-type+result-format->fields :collection (assoc query :result-format :atom)))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :json]
  [concept-type query]
  (elastic-search-index/concept-type+result-format->fields :granule (assoc query :result-format :atom)))

(defmethod elastic-results/elastic-results->query-results :json
  [context query elastic-results]
  (elastic-results/elastic-results->query-results context (assoc query :result-format :atom) elastic-results))

(defmethod gcrf/query-results->concept-ids :json
  [results]
  (->> results
       :items
       (map :id)))

(defmulti atom-reference->json
  "Converts a search result atom reference into json"
  (fn [results concept-type reference]
    concept-type))

(defn- fix-ocsd-values
  "Convert the keys in a map to underscore form and converts values to strings (because
  that is how it is in ECHO json)."
  [input-map]
  (into {} (for [[k v] input-map] [(csk/->snake_case_string (name k)) (str v)])))

(defmethod atom-reference->json :collection
  [results concept-type reference]
  (let [{:keys [has-granules-map granule-counts-map]} results
        {:keys [id score title short-name version-id summary updated dataset-id collection-data-type
                processing-level-id original-format data-center archive-center start-date end-date
                atom-links associated-difs online-access-flag browse-flag coordinate-system shapes
                orbit-parameters]} reference
        shape-result (atom-spatial/shapes->json shapes)
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
                       :processing_level_id processing-level-id
                       :time_start start-date
                       :time_end end-date
                       :dif_ids associated-difs
                       :online_access_flag online-access-flag
                       :browse_flag browse-flag
                       :has_granules (when has-granules-map (get has-granules-map id false))
                       :granule_count (when granule-counts-map (get granule-counts-map id 0))
                       :links (seq (map atom/atom-link->attribute-map atom-links))
                       :coordinate_system coordinate-system
                       :orbit_parameters (when orbit-parameters
                                           (fix-ocsd-values orbit-parameters))}
                      shape-result)]
    ;; remove entries with nil value
    (util/remove-nil-keys result)))

(defmethod atom-reference->json :granule
  [results concept-type reference]
  (let [{:keys [id title updated dataset-id producer-gran-id size original-format
                data-center start-date end-date atom-links online-access-flag browse-flag
                day-night cloud-cover coordinate-system shapes
                orbit-calculated-spatial-domains]} reference
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
                       :orbit_calculated_spatial_domains (map
                                                           fix-ocsd-values
                                                           orbit-calculated-spatial-domains)}
                      shape-result)]
    ;; remove entries with nil value
    (util/remove-nil-keys result)))

(defmethod qs/search-results->response :json
  [context query results]
  (let [{:keys [items facets]} results
        {:keys [concept-type result-format]} query
        items (if (= :granule (:concept-type query))
                (atom/append-collection-links context items)
                items)
        response-results {:feed
                          (util/remove-nil-keys
                            {:updated (str (time/now))
                             :id (url/atom-request-url context concept-type result-format)
                             :title (atom/concept-type->atom-title concept-type)
                             :entry (map (partial atom-reference->json results concept-type) items)
                             :facets facets})}]
    (json/generate-string response-results {:pretty (:pretty? query)})))
