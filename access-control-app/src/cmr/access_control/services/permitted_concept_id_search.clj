(ns cmr.access-control.services.permitted-concept-id-search
  "Contains ACL search functions for permitted-concept-id searches"
  (:require
    [clj-time.core :as t]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.elastic-utils.index-util :as index-util]
    [cmr.transmit.metadata-db2 :as mdb2]
    [cmr.umm-spec.time :as spec-time]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm.start-end-date :as umm-lib-time]
    [cmr.umm.umm-core :as umm-lib]))

(defn- make-keyword
  "Merges concept-type and keyword into one keyword."
  [concept-type k]
  (keyword (str (name concept-type) "-" (name k))))

(defn- create-generic-condition
  "Constructs query condition for acls without an identifier"
  []
  (gc/and
    (common-qm/boolean-condition :granule-identifier false)
    (common-qm/boolean-condition :collection-identifier false)))

(defn- create-temporal-intersect-condition
  "Constructs query condition for intersect mask"
  [start-date stop-date concept-type]
  (gc/and
    (common-qm/string-condition (make-keyword concept-type :temporal-mask) "intersect" true false)
    (gc/or
      (gc/and
        ;; The ACL start date must be greater than the collection's start date and
        ;; less than the collection's stop date
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-start-date) nil stop-date)
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-start-date) start-date nil))
      (gc/and
        ;; The ACL stop date must be less than the collection's stop date and
        ;; greater than the collection's start date
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-stop-date) start-date nil)
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-stop-date) nil stop-date))
      (gc/and
        ;; The ACL stop date must be greater than the collection stop date and
        ;; the ACL start date must be less than the collection start date
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-start-date) nil start-date)
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-stop-date) stop-date nil))
      (gc/and
        ;; The ACL stop date must be less than the collection's stop date and
        ;; the ACL start date must be greater than the collection's start date
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-stop-date) nil stop-date)
        (common-qm/date-range-condition (make-keyword concept-type :temporal-range-start-date) start-date nil)))))

(defn- create-temporal-disjoint-condition
  "Constructs query condition for disjoint mask"
  [start-date stop-date concept-type]
  (gc/and
    (common-qm/string-condition (make-keyword concept-type :temporal-mask) "disjoint" true false)
    (gc/or
      ;; The ACL start date is greater than the collection's stop date or
      ;; THE ACL stop date is less than the collection's start date
      (common-qm/date-range-condition (make-keyword concept-type :temporal-range-start-date) stop-date nil true)
      (common-qm/date-range-condition (make-keyword concept-type :temporal-range-stop-date) nil start-date true))))

(defn create-temporal-contains-condition
  "Constructs query condiiton for contains mask"
  [start-date stop-date concept-type]
  (gc/and
    ;; The ACL start date must be less than the collection's start date and
    ;; the ACL stop date must be greater than the collection's stop date
    (common-qm/string-condition (make-keyword concept-type :temporal-mask) "contains" true false)
    (common-qm/date-range-condition (make-keyword concept-type :temporal-range-start-date) nil start-date)
    (common-qm/date-range-condition (make-keyword concept-type :temporal-range-stop-date) stop-date nil)))

(defn- create-temporal-condition
  "Constructs query condition for searching permitted_concept_ids by temporal"
  [parsed-metadata concept-type]
  (let [start-date (if (= concept-type :granule)
                     (umm-lib-time/start-date :granule (:temporal parsed-metadata))
                     (spec-time/collection-start-date parsed-metadata))
        stop-date (if (= concept-type :granule)
                    (umm-lib-time/end-date :granule (:temporal parsed-metadata))
                    (spec-time/collection-end-date parsed-metadata))
        stop-date (if (or (= :present stop-date) (nil? stop-date))
                    (t/now)
                    stop-date)]
    (gc/or
       (create-temporal-contains-condition start-date stop-date concept-type)
       (create-temporal-intersect-condition start-date stop-date concept-type)
       (create-temporal-disjoint-condition start-date stop-date concept-type))))

(defn- create-access-value-condition
  "Constructs query condition for searching permitted_concept_ids by access-value."
  [parsed-metadata concept-type]
  ;; If there are no access values present in the concept then the include undefined
  ;; value is used.
  (if-let [access-value (or (:access-value parsed-metadata) (:Value (:AccessConstraints parsed-metadata)))]
    (common-qm/numeric-range-intersection-condition
      (make-keyword concept-type :access-value-min)
      (make-keyword concept-type :access-value-max)
      access-value
      access-value)
    (common-qm/boolean-condition (make-keyword concept-type :access-value-include-undefined-value) true)))

(defn- create-entry-title-condition
  "Constructs query condition for searching permitted_concept_ids by entry_titles"
  [parsed-metadata]
  (if-let [entry-title (:EntryTitle parsed-metadata)]
    (common-qm/string-condition :entry-title entry-title true false)
    common-qm/match-none))

(defn- get-collection-conditions
  "Constructs permitted_concept_id search conditions for given collection"
  [collection-metadata]
  (gc/or
    (create-generic-condition)
    (create-access-value-condition collection-metadata :collection)
    (create-temporal-condition collection-metadata :collection)
    (create-entry-title-condition collection-metadata)))

(defn- get-granule-conditions
  "Constructs permitted_concept-id search conditions
   for given granule and parent collection"
  [granule-metadata parent-collection-metadata]
  (gc/or
    (create-generic-condition)
    (gc/and
      (gc/or
        (gc/and
          (common-qm/negated-condition (common-qm/exist-condition :granule-temporal-range-start-date))
          (common-qm/negated-condition (common-qm/exist-condition :granule-temporal-range-stop-date)))
        (create-temporal-condition granule-metadata :granule))
      (gc/or
        (gc/and
          (common-qm/negated-condition (common-qm/exist-condition :granule-access-value-include-undefined-value))
          (common-qm/negated-condition (common-qm/exist-condition :granule-access-value-min)))
        (create-access-value-condition granule-metadata :granule))
      (gc/or
        (gc/and
          (common-qm/negated-condition (common-qm/exist-condition :collection-temporal-range-start-date))
          (common-qm/negated-condition (common-qm/exist-condition :collection-temporal-range-stop-date)))
        (create-temporal-condition parent-collection-metadata :collection))
      (gc/or
        (gc/and
          (common-qm/negated-condition (common-qm/exist-condition :collection-access-value-include-undefined-value))
          (common-qm/negated-condition (common-qm/exist-condition :collection-access-value-min)))
        (create-access-value-condition parent-collection-metadata :collection))
      (gc/or
        (common-qm/negated-condition (common-qm/exist-condition :entry-title))
        (create-entry-title-condition parent-collection-metadata)))))

(defn get-permitted-concept-id-conditions
  "Returns query to search for ACLs that could permit given concept"
  [context concept]
  (let [concept-type (:concept-type concept)
        parsed-metadata (if (= concept-type :collection)
                          (umm-spec/parse-metadata (merge {:ignore-kms-keywords true} context) concept)
                          (umm-lib/parse-concept concept))
        parent-collection (when (= concept-type :granule)
                            (mdb2/get-latest-concept context (get-in concept [:extra-fields :parent-collection-id])))
        parsed-parent-collection-metadata (when parent-collection
                                            (umm-spec/parse-metadata (merge {:ignore-kms-keywords true} context) parent-collection))]
    (gc/and
      (common-qm/string-condition :provider (:provider-id concept))
      (common-qm/boolean-condition (make-keyword concept-type :applicable) true)
      (if (= concept-type :collection)
        (get-collection-conditions parsed-metadata)
        (get-granule-conditions parsed-metadata parsed-parent-collection-metadata)))))
