(ns cmr.access-control.services.permitted-concept-id-search
  "Contains ACL search functions for permitted-concept-id searches"
  (:require
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.umm-spec.umm-spec-core :as umm-spec]))

(defn- create-generic-permitted-concept-id-condition
  "Constructs query condition for searching permitted_concept_ids by
   collection-applicable acls without a collection identifier"
  []
  (gc/group-conds
    :and
    [(common-qm/boolean-condition :collection-identifier false)
     (common-qm/boolean-condition :collection-applicable true)]))

(defn- create-access-value-condition
  "Constructs query condition for searching permitted_concept_ids by access-value."
  [parsed-metadata]
  (gc/group-conds
    :and
    ;; If there are no access values present in the concept then the include undefined
    ;; value is used.
    [(if-let [access-value (:Value (:AccessConstraints parsed-metadata))]
       (common-qm/numeric-range-intersection-condition
         :collection-access-value-min
         :collection-access-value-max
         access-value
         access-value)
       (common-qm/boolean-condition :collection-access-value-include-undefined-value true))
     (common-qm/boolean-condition :collection-applicable true)]))

(defn get-permitted-concept-id-conditions
  "Returns query to search for ACLs that could permit given concept"
  [context concept]
  (let [parsed-metadata (umm-spec/parse-metadata (merge {:ignore-kms-keywords true} context) concept)]
    (gc/group-conds
     :and
     [(common-qm/string-condition :provider (:provider-id concept))
      (gc/group-conds
       :or
       [(create-generic-permitted-concept-id-condition)
        (create-access-value-condition parsed-metadata)])])))
