(ns cmr.search.services.query-execution.facets.granule-v2-facets
  "Functions for generating v2 granule facets. Similar structure as v2 collection facets, but
  granule fields. First major use case is supporting OPeNDAP virutal directories capability."
  (:require
   [cmr.common.util :as util]))

(def granule-facet-params->elastic-fields
  {})

(def granule-facet-fields
  "Faceted fields for granules."
  (keys granule-facet-params->elastic-fields))

(def granule-fields->aggregation-fields
  "Defines the mapping of granule parameter names to the aggregation parameter names."
  {})

(def granule-v2-facets-root
  "Root element for the facet response"
  {:title "Browse Granules"})

(def v2-facets-result-field-in-order
  "Defines the v2 facets result field in order"
  [])

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
