(ns cmr.search.services.query-execution.facets.granule-v2-facets
  "Functions for generating v2 granule facets. Similar structure as v2 collection facets, but
  granule fields. First major use case is supporting OPeNDAP virutal directories capability."
  (:require
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
   [cmr.search.services.query-execution.facets.temporal-facets :as temporal-facets]))

(def granule-facet-params->elastic-fields
  {:start-date :start-date-doc-values})

(def granule-facet-fields
  "Faceted fields for granules."
  (keys granule-facet-params->elastic-fields))

(def granule-fields->aggregation-fields
  "Defines the mapping of granule parameter names to the aggregation parameter names."
  (into {}
        (map (fn [field]
               [field (q2e/query-field->elastic-field field :granule)])
             granule-facet-fields)))

(def granule-v2-facets-root
  "Root element for the facet response"
  {:title "Browse Granules"})

(def v2-facets-result-field-in-order
  "Defines the v2 facets result field in order"
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

(def validator
  "Returns a validator function to perform the granule v2 facets validations."
  (util/build-validator :bad-request validations))

(defn create-v2-facets
  "Return the granule specific V2 facets"
  [base-url query-params aggs _ _]
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
