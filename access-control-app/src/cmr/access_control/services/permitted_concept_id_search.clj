(ns cmr.access-control.services.permitted-concept-id-search
  "Contains ACL search functions for permitted-concept-id searches"
  (:require
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.umm.start-end-date :as sed]
    [cmr.search.models.query :as q]
    [cmr.umm-spec.umm-spec-core :as umm-spec]))

(defn- create-temporal-condition
  "Constructs query condition for searching permitted_concept_ids by temporal"
  [parsed-metadata]
  (let [start-date (sed/start-date :collection (:temporal parsed-metadata))
        stop-date (sed/end-date :collection (:temporal parsed-metadata))]
    (gc/group-conds
      :or
      [(gc/group-conds
         :and
         [(common-qm/string-condition :temporal-mask "contains")
          (common-qm/date-range-condition :temporal-range-start start-date stop-date false)
          (common-qm/date-range-condition :temporal-range-stop start-date stop-date false)])
       (gc/group-conds
         :and
         [(common-qm/string-condition :temporal-mask "disjoint")
          (common-qm/negated-condition ((q/map->TemporalCondition {:temporal-range-start start-date
                                                                   :temporal-range-stop stop-date
                                                                   :exclusive? false})))])
       (gc/group-conds
         :and
         [(common-qm/string-condition :temporal-mask "intersect")
          (q/map->TemporalCondition {:temporal-range-start start-date
                                     :temporal-range-stop stop-date
                                     :exclusive? false})])])))


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

(defn- create-provider-condition
  "Constructs query condition for searching permitted_concept_id by provider"
  [provider-id]
  (gc/group-conds
    :and
    [(common-qm/boolean-condition :collection-applicable true)
     (common-qm/boolean-condition :collection-identifier false)
     (common-qm/string-condition :provider provider-id false false)]))

(defn get-permitted-concept-id-conditions
  "Returns query to search for ACLs that could permit given concept"
  [context concept]
  (let [parsed-metadata (umm-spec/parse-metadata (merge {:ignore-kms-keywords true} context) concept)]
    (gc/group-conds
     :or
     [(create-provider-condition (:provider-id concept))
      (create-access-value-condition parsed-metadata)])))
