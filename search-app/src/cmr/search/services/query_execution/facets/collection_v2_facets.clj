(ns cmr.search.services.query-execution.facets.collection-v2-facets
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as string]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
   [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]
   [cmr.search.services.query-execution.facets.links-helper :as lh]))

(def collection-facets-v2-params->elastic-fields
  "Defines the mapping of the base search parameters for the v2 facets fields to
   its field names in elasticsearch."
  {:science-keywords :science-keywords-humanized
   :platforms :platforms2-humanized
   :instrument :instrument-sn-humanized
   :data-center :organization-humanized
   :project :project-sn-humanized
   :processing-level-id :processing-level-id-humanized
   :variables :variables
   :granule-data-format :granule-data-format-humanized
   :latency :latency
   :two-d-coordinate-system-name :two-d-coord-name
   :horizontal-data-resolution-range :horizontal-data-resolutions})

(defmethod v2-facets/facets-v2-params->elastic-fields :collection
  [_]
  collection-facets-v2-params->elastic-fields)

(def collection-facets-v2-params
  "Collection facets parameters."
  (keys collection-facets-v2-params->elastic-fields))

(def collection-facets-v2-params-with-default-size
  "A map with collection facets parameters and the default term size values."
  (zipmap collection-facets-v2-params
          (repeat (count collection-facets-v2-params) v2-facets/DEFAULT_TERMS_SIZE)))

(defmethod v2-facets/facets-v2-params :collection
  [_]
  collection-facets-v2-params)

(defmethod v2-facets/facets-v2-params-with-default-size :collection
  [_]
  collection-facets-v2-params-with-default-size)

(def collection-facet-fields->aggregation-fields
  "Defines the mapping between facet fields to aggregation fields."
  (into {}
        (map (fn [field] [field (if (= field :horizontal-data-resolution-range)
                                  field
                                  (keyword (str (name field) "-h")))])
             collection-facets-v2-params)))

(defmethod v2-facets/facet-fields->aggregation-fields :collection
  [_]
  collection-facet-fields->aggregation-fields)

(defmethod v2-facets/v2-facets-result-field-in-order :collection
  [_]
  ["Keywords" "Platforms" "Instruments" "Organizations" "Projects" "Processing levels" "Measurements"
   "Output File Formats" "Reprojections" "Tiling System" "Horizontal Data Resolution" "Latency"])

(defmethod v2-facets/v2-facets-root :collection
  [_]
  {:title "Browse Collections"
   :type :group})

(defconfig include-variable-facets
  "Controls whether or not to display variable facets. Feature toggle needed while prototyping
  with EDSC in certain environments."
  {:type Boolean :default false})

(defn create-terms-v2-facets
  "Parses the elastic aggregations and generates the v2 facets for the
   terms field."
  [elastic-aggregations base-url query-params facet-field]
  (let [search-terms-from-query (lh/get-values-for-field query-params facet-field)
        value-counts (v2-facets/add-terms-with-zero-matching-collections
                      (frf/buckets->value-count-pairs (get elastic-aggregations facet-field))
                      search-terms-from-query)
        snake-case-field (string/replace (csk/->snake_case_string facet-field) #"_h" "")
        applied? (some? (or (get query-params snake-case-field)
                            (get query-params (str snake-case-field "[]"))))
        query-field (keyword (string/replace (name facet-field) #"-h" ""))
        children (map (v2h/filter-node-generator base-url query-params query-field applied?)
                      (sort-by first util/compare-natural-strings value-counts))]
    (when (seq children)
      (vector
       (v2h/generate-group-node (facet-field v2h/fields->human-readable-label) applied?
                                children)))))

(defmethod v2-facets/create-v2-facets-by-concept-type :collection
  [concept-type base-url query-params aggs facet-fields]
  (let [flat-facet-fields (remove #{:science-keywords :platforms
                                    :variables
                                    :horizontal-data-resolution-range} facet-fields)
        facet-fields-set (set facet-fields)
        ;; CMR-4682: Refactor out collection and granule specific logic
        science-keywords-facets (when (facet-fields-set :science-keywords)
                                  (hv2/create-hierarchical-v2-facets
                                   aggs base-url query-params :science-keywords-h))
        platform-facets (when (facet-fields-set :platforms)
                          (hv2/create-hierarchical-v2-facets
                           aggs base-url query-params :platforms-h))
        variables-facets (when (and (facet-fields-set :variables) (include-variable-facets))
                           (hv2/create-hierarchical-v2-facets
                            aggs base-url query-params :variables-h))
        two-d-facets (when (facet-fields-set :two-d-coordinate-system-name)
                       (create-terms-v2-facets
                        aggs base-url query-params :two-d-coordinate-system-name-h))
        latency-facets (when (facet-fields-set :latency)
                         (create-terms-v2-facets
                          aggs base-url query-params :latency-h))
        range-facets (when (facet-fields-set :horizontal-data-resolution-range)
                       (v2-facets/create-prioritized-v2-facets
                        :collection aggs [:horizontal-data-resolution-range] base-url query-params false))
        v2-facets (concat science-keywords-facets
                          platform-facets
                          (v2-facets/create-prioritized-v2-facets
                           :collection aggs flat-facet-fields base-url query-params)
                          variables-facets
                          two-d-facets
                          latency-facets
                          range-facets)]
    v2-facets))
