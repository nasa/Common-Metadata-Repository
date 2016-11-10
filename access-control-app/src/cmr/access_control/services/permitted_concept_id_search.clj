(ns cmr.access-control.services.permitted-concept-id-search
  "Contains ACL search functions for permitted-concept-id searches"
  (:require
    [clj-time.core :as t]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.elastic-utils.index-util :as index-util]
    [cmr.umm-spec.time :as spec-time]
    [cmr.search.models.query :as q]
    [cmr.umm-spec.umm-spec-core :as umm-spec]))

(defn- create-temporal-condition
  "Constructs query condition for searching permitted_concept_ids by temporal"
  [parsed-metadata]
  (let [start-date (index-util/date->elastic (spec-time/collection-start-date parsed-metadata))
        stop-date (index-util/date->elastic (spec-time/collection-end-date parsed-metadata))
        now (index-util/date->elastic (t/now))
        floor-date (index-util/date->elastic (t/date-time 1970))]
    (proto-repl.saved-values/save 1)
    (gc/group-conds
      :or
      [(gc/group-conds
         :and
         [(common-qm/string-condition :temporal-mask "contains")
          (common-qm/date-range-condition :temporal-range-start-date floor-date start-date false)
          (common-qm/date-range-condition :temporal-range-stop-date stop-date now false)])])))
       ;(gc/group-conds
       ;  :and
       ;  [(common-qm/string-condition :temporal-mask "disjoint")
       ;   (common-qm/negated-condition (q/map->TemporalCondition {:temporal-range-start start-date
       ;                                                           :temporal-range-stop stop-date
       ;                                                           :exclusive? false)])])))
       ;(gc/group-conds
       ;  :and
       ;  [(common-qm/string-condition :temporal-mask "intersect")
       ;   (q/map->TemporalCondition {:temporal-range-start-date start-date
       ;                              :temporal-range-stop-date stop-date
       ;                              :exclusive? false)])])))


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
     [(create-temporal-condition parsed-metadata)
      (create-provider-condition (:provider-id concept))
      (create-access-value-condition parsed-metadata)])))
