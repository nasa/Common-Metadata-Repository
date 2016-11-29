(ns cmr.access-control.services.permitted-concept-id-search
  "Contains ACL search functions for permitted-concept-id searches"
  (:require
    [clj-time.core :as t]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.elastic-utils.index-util :as index-util]
    [cmr.umm-spec.time :as spec-time]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm.start-end-date :as umm-lib-time]
    [cmr.umm.umm-core :as umm-lib]))

(defn- create-generic-applicable-condition
  "Constructs query condition for collection-applicable acls without a collection identifier"
  [applicable identifier]
  (gc/and-conds
    [(common-qm/boolean-condition applicable true)
     (common-qm/boolean-condition identifier false)]))

(defn- create-temporal-intersect-condition
  "Constructs query condition for intersect mask"
  [start-date stop-date]
  (gc/and-conds
    [(common-qm/string-condition :temporal-mask "intersect" true false)
     (gc/or-conds
       [(gc/and-conds
          ;; The ACL start date must be greater than the collection's start date and
          ;; less than the collection's stop date
          [(common-qm/date-range-condition :temporal-range-start-date nil stop-date)
           (common-qm/date-range-condition :temporal-range-start-date start-date nil)])
        (gc/and-conds
          ;; The ACL stop date must be less than the collection's stop date and
          ;; greater than the collection's start date
          [(common-qm/date-range-condition :temporal-range-stop-date start-date nil)
           (common-qm/date-range-condition :temporal-range-stop-date nil stop-date)])
        (gc/and-conds
          ;; The ACL stop date must be greater than the collection stop date and
          ;; the ACL start date must be less than the collection start date
          [(common-qm/date-range-condition :temporal-range-start-date nil start-date)
           (common-qm/date-range-condition :temporal-range-stop-date stop-date nil)])
        (gc/and-conds
          ;; The ACL stop date must be less than the collection's stop date and
          ;; the ACL start date must be greater than the collection's start date
          [(common-qm/date-range-condition :temporal-range-stop-date nil stop-date)
           (common-qm/date-range-condition :temporal-range-start-date start-date nil)])])]))

(defn- create-temporal-disjoint-condition
  "Constructs query condition for disjoint mask"
  [start-date stop-date]
  (gc/and-conds
    [(common-qm/string-condition :temporal-mask "disjoint" true false)
     (gc/or-conds
       ;; The ACL start date is greater than the collection's stop date or
       ;; THE ACL stop date is less than the collection's start date
       [(common-qm/date-range-condition :temporal-range-start-date stop-date nil true)
        (common-qm/date-range-condition :temporal-range-stop-date nil start-date true)])]))

(defn create-temporal-contains-condition
  "Constructs query condiiton for contains mask"
  [start-date stop-date]
  (gc/and-conds
      ;; The ACL start date must be less than the collection's start date and
      ;; the ACL stop date must be greater than the collection's stop date
     [(common-qm/string-condition :temporal-mask "contains" true false)
      (common-qm/date-range-condition :temporal-range-start-date nil start-date)
      (common-qm/date-range-condition :temporal-range-stop-date stop-date nil)]))

(defn- create-temporal-condition
  "Constructs query condition for searching permitted_concept_ids by temporal"
  [parsed-metadata applicable]
  (let [start-date (if (= applicable :granule-applicable)
                     (umm-lib-time/start-date :granule (:temporal parsed-metadata))
                     (spec-time/collection-start-date parsed-metadata))
        stop-date (if (= applicable :granule-applicable)
                    (umm-lib-time/start-date :granule (:temporal parsed-metadata))
                    (spec-time/collection-end-date parsed-metadata))
        stop-date (if (= :present stop-date)
                    (t/now)
                    stop-date)]
    (gc/and-conds
      [(common-qm/boolean-condition applicable true)
       (gc/or-conds
         [(create-temporal-contains-condition start-date stop-date)
          (create-temporal-intersect-condition start-date stop-date)
          (create-temporal-disjoint-condition start-date stop-date)])])))

(defn- create-access-value-condition
  "Constructs query condition for searching permitted_concept_ids by access-value."
  [parsed-metadata applicable]
  (gc/and-conds
    ;; If there are no access values present in the concept then the include undefined
    ;; value is used.
    [(if-let [access-value (or (:access-value parsed-metadata) (:Value (:AccessConstraints parsed-metadata)))]
       (common-qm/numeric-range-intersection-condition
         :access-value-min
         :access-value-max
         access-value
         access-value)
       (common-qm/boolean-condition :access-value-include-undefined-value true))
     (common-qm/boolean-condition applicable true)]))

(defn get-permitted-concept-id-conditions
  "Returns query to search for ACLs that could permit given concept"
  [context concept]
  (let [concept-type (:concept-type concept)
        parsed-metadata (if (= concept-type :collection)
                          (umm-spec/parse-metadata (merge {:ignore-kms-keywords true} context) concept)
                          (umm-lib/parse-concept concept))
        identifier (keyword (str (name concept-type) "-identifier"))
        applicable (keyword (str (name concept-type) "-applicable"))]
    (gc/and-conds
     [(common-qm/string-condition :provider (:provider-id concept))
      (gc/or-conds
        [(create-generic-applicable-condition applicable identifier)
         (create-access-value-condition parsed-metadata applicable)
         (create-temporal-condition parsed-metadata applicable)])])))
