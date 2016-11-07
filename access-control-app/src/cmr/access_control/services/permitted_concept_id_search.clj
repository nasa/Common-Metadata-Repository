(ns cmr.access-control.services.permitted-concept-id-search
  "Contains ACL search functions for permitted-concept-id searches"
  (:require
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.umm-spec.umm-spec-core :as umm-spec]))

(defn- create-access-value-condition
  "Constructs query condition for searching permitted_concept_ids by access-value."
  [parsed-concept]
  (if-let [access-value (:Value (:AccessConstraints parsed-concept))]
    (common-qm/numeric-range-intersection-condition
      :collection-access-value-min
      :collection-access-value-max
      access-value
      access-value)
    (common-qm/boolean-condition :collection-access-value-include-undefined-value true)))

(defn get-permitted-concept-id-conditions
  "Returns query to search for ACLs that could permit given concept"
  [context concept]
  (let [parsed-concept (umm-spec/parse-metadata context concept)]
    (gc/group-conds
      :or
      [(common-qm/boolean-condition :collection-identifier false)
       (create-access-value-condition parsed-concept)])))
