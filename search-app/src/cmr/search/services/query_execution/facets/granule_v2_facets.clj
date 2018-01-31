(ns cmr.search.services.query-execution.facets.granule-v2-facets
  "Functions for generating v2 granule facets. Similar structure as v2 collection facets, but
  granule fields. First major use case is supporting OPeNDAP virutal directories capability."
  (:require
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
   [cmr.search.services.query-execution.facets.temporal-facets :as temporal-facets]))

(def granule-facet-params->elastic-fields
  "Maps the parameter names for the concept-type to the fields in Elasticsearch."
  {:start-date :start-date-doc-values})

(defmethod v2-facets/facets-v2-params->elastic-fields :granule
  [_]
  granule-facet-params->elastic-fields)

(def granule-facet-params
  "Granule facet params."
  (keys granule-facet-params->elastic-fields))

(defmethod v2-facets/facets-v2-params :granule
  [_]
  granule-facet-params)

(def granule-fields->aggregation-fields
  "Defines the mapping of granule parameter names to the aggregation parameter names."
  (into {}
        (map (fn [field]
               [field (q2e/query-field->elastic-field field :granule)])
             granule-facet-params)))

(defmethod v2-facets/facet-fields->aggregation-fields :granule
  [_]
  granule-fields->aggregation-fields)

(defmethod v2-facets/v2-facets-root :granule
  [_]
  {:title "Browse Granules"})

(defmethod v2-facets/v2-facets-result-field-in-order :granule
  [_]
  ["Temporal"])

(defn single-collection-validation
  "Validates that the provided query is limited to a single collection. We do this to prevent
  expensive aggregation queries that would have to run against more than one granule index."
  [context]
  (let [collection-ids (:query-collection-ids context)
        collection-count (count collection-ids)]
    (when-not (= 1 collection-count)
      [(format "Granule V2 facets are limited to a single collection, but query matched %s collections."
               (if (= 0 collection-count) "an undetermined number of" collection-count))])))

(def validations
  "Validation functions to run for v2 granule facets."
  (util/compose-validations [single-collection-validation]))

(defmethod v2-facets/facets-validator :granule
  [_]
  (util/build-validator :bad-request validations))

(defmethod v2-facets/create-v2-facets-by-concept-type :granule
  [concept-type base-url query-params aggs _]
  (let [temporal-facets (temporal-facets/parse-temporal-buckets
                         (-> aggs :start-date-doc-values :buckets)
                         :year)
        year-node (when (seq temporal-facets)
                    (v2h/generate-group-node "Year"
                                              false
                                              temporal-facets))
        ;; Will be added by CMR-4683 and CMR-4684
        year-node (dissoc year-node :applied :type)
        temporal-node (when year-node
                        (v2h/generate-group-node (v2h/fields->human-readable-label :temporal)
                                                 false
                                                 [year-node]))
        temporal-node (dissoc temporal-node :applied :type)]
     [temporal-node]))
