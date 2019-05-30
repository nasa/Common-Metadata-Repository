(ns cmr.access-control.services.permitted-concept-id-search
  "Contains ACL search functions for permitted-concept-id searches"
  (:require
    [clj-time.core :as t]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.elastic-utils.index-util :as index-util]
    [cmr.transmit.metadata-db2 :as mdb2]
    [cmr.umm-spec.legacy :as legacy]
    [cmr.umm-spec.time :as spec-time]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm.start-end-date :as umm-lib-time]))

(defn- make-keyword
  "Merges concept-type and keyword into one keyword."
  [concept-type k]
  (keyword (str (name concept-type) "-" (name k))))

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
    ;; The ACL stop date is less than the collection's start date
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
  [concept-type parsed-metadata]
  (let [start-date (if (= concept-type :granule)
                     (umm-lib-time/start-date :granule (:temporal parsed-metadata))
                     (spec-time/collection-start-date parsed-metadata))
        stop-date (if (= concept-type :granule)
                    (umm-lib-time/end-date :granule (:temporal parsed-metadata))
                    (spec-time/collection-end-date parsed-metadata))
        stop-date (if (or (= :present stop-date) (nil? stop-date))
                    (t/now)
                    stop-date)]
    ;; Temporal condition finds ACLs when one of the temporal mask ranges match or
    ;; there is no temporal specified at all.
    (gc/or
     (create-temporal-contains-condition start-date stop-date concept-type)
     (create-temporal-intersect-condition start-date stop-date concept-type)
     (create-temporal-disjoint-condition start-date stop-date concept-type)
     ;; no temporal specified
     (common-qm/not-exist-condition (make-keyword concept-type :temporal-mask)))))

(defn- create-access-value-condition
  "Constructs query condition for searching permitted_concept_ids by access-value."
  [concept-type parsed-metadata]
  ;; Access value condition finds the ACL when:
  ;; access value match through range if there is access value in the concept;
  ;; or through matching undefined value if there is no access value in the concept;
  ;; or there is no access value filters at all.
  (gc/or
   (if-let [access-value (or (:access-value parsed-metadata)
                             (:Value (:AccessConstraints parsed-metadata)))]
     (common-qm/numeric-range-intersection-condition
      (make-keyword concept-type :access-value-min)
      (make-keyword concept-type :access-value-max)
      access-value
      access-value)
     ;; If there are no access values in the concept then it can match through include-undefined-value
     (common-qm/boolean-condition
      (make-keyword concept-type :access-value-include-undefined-value) true))
   ;; there is no access value filters at all
   (gc/and
    (common-qm/not-exist-condition (make-keyword concept-type :access-value-min))
    (common-qm/not-exist-condition (make-keyword concept-type :access-value-max))
    (common-qm/not-exist-condition
     (make-keyword concept-type :access-value-include-undefined-value)))))

(defn- create-entry-tite-condition
  "Returns the entry title query condition for the given parsed collection metadata"
  [parsed-metadata]
  ;; Entry title condition finds ACLs when entry title match
  ;; or there is no entry title specified at all
  (let [entry-title (:EntryTitle parsed-metadata)]
    (gc/or
     (common-qm/string-condition :entry-title entry-title true false)
     (common-qm/not-exist-condition :entry-title))))

(defn- collection-identifier-match-condition
  "Returns the query condition when collection identifier matches the given concept type
   and parsed metadata"
  [parsed-metadata]
  (gc/and
   (create-access-value-condition :collection parsed-metadata)
   (create-temporal-condition :collection parsed-metadata)
   (create-entry-tite-condition parsed-metadata)))

(defmulti get-permitted-concept-id-conditions
  "Returns the query to search for ACLs that could permit the given concept"
  (fn [context concept]
    (:concept-type concept)))

(defmethod get-permitted-concept-id-conditions :collection
  [context concept]
  (let [parsed-metadata (umm-spec/parse-metadata
                         (assoc context :ignore-kms-keywords true) concept)]
    (gc/and
     (common-qm/string-condition :provider (:provider-id concept))
     (common-qm/boolean-condition :collection-applicable true)
     ;; there is no collection identifier or the collection identifier conditions match
     (gc/or
      (common-qm/boolean-condition :collection-identifier false)
      (collection-identifier-match-condition parsed-metadata)))))

(defmethod get-permitted-concept-id-conditions :granule
  [context concept]
  (let [parsed-metadata (legacy/parse-concept context concept)
        parent-collection (mdb2/get-latest-concept
                           context (get-in concept [:extra-fields :parent-collection-id]))
        parsed-collection-metadata (umm-spec/parse-metadata
                                    (assoc context :ignore-kms-keywords true) parent-collection)]
    (gc/and
     (common-qm/string-condition :provider (:provider-id concept))
     (common-qm/boolean-condition :granule-applicable true)
     ;; collection identifier condition that matches on collection identifiers in the ACL
     (collection-identifier-match-condition parsed-collection-metadata)
     ;; there is no granule identifier or the granule identifier conditions match
     (gc/or
      (common-qm/boolean-condition :granule-identifier false)
      (gc/and
       ;; granule identifier access value condition, i.e. access value either match or not exist
       (create-access-value-condition :granule parsed-metadata)
       ;; granule identifier temporal condition, i.e. temporal either match or not exist
       (create-temporal-condition :granule parsed-metadata))))))
