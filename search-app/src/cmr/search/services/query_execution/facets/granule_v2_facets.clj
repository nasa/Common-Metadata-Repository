(ns cmr.search.services.query-execution.facets.granule-v2-facets
  "Functions for generating v2 granule facets. Similar structure as v2 collection facets, but
  granule fields. First major use case is supporting OPeNDAP virutal directories capability.")

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
