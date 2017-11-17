(ns cmr.search.models.query
  "Defines various query models and conditions specific for searching for collections and granules."
  (:require
   [cmr.common-app.services.search.query-model :as common-qm]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord SpatialCondition
  [
   ;; One of cmr.spatial polygon, line, point, or mbr
   shape])

(defrecord TemporalCondition
  [
   start-date
   end-date
   start-day
   end-day
   exclusive?])


(defrecord OrbitNumberValueCondition
  [
   value])


(defrecord OrbitNumberRangeCondition
  [
   min-value
   max-value])


(defrecord EquatorCrossingLongitudeValueCondition
  [
   value])


(defrecord EquatorCrossingLongitudeRangeCondition
  [
   min-value
   max-value])


;; This condition is used for holding two-d-coordinate value
(defrecord CoordinateValueCondition
  [
   value])


;; This condition is used for holding two-d-coordinate range
(defrecord CoordinateRangeCondition
  [
   min-value
   max-value])


;; This condition is used for holding two-d-coordinate-system coordinates info
(defrecord TwoDCoordinateCondition
  [
   ;; it is nil, CoordinateValueCondition or CoordinateRangeCondition
   coordinate-1-cond
   ;; it is nil, CoordinateValueCondition or CoordinateRangeCondition
   coordinate-2-cond])


(defrecord TwoDCoordinateSystemCondition
  [
   two-d-name
   ;; it is nil or a list of TwoDCoordinateConditions
   two-d-conditions
   ;; indicates if the search is case sensitive. Defaults to false.
   case-sensitive?])

(defrecord EquatorCrossingDateCondition
  [
   start-date
   end-date])


(defrecord CollectionQueryCondition
  [
   ;; The condition to find collections
   condition])

(def attribute-types
  "A list of valid additional attribute search types"
  [:float :int :string :date :time :datetime])

;; Condition for searching against either an additional attribute name or group. One of name or
;; group must be present.
(defrecord AttributeNameAndGroupCondition
  [
   ;; Optional - The name of the additional attribute being searched against.
   name

   ;; Optional - UMM Group field. In DIF9 and DIF10 metadata this is generally a namespace string.
   group

   ;; Optional - Performs pattern search on both name and group fields. Nil defaults to false.
   pattern?])


;; Condition for searching against a given additional attribute for an exact value.
(defrecord AttributeValueCondition
  [
   ;; Required - The type of the attribute being searched against (float, datetime, string, etc)
   type

   ;; Required - The name of the additional attribute being searched against.
   name

   ;; Optional - UMM Group field. In DIF9 and DIF10 metadata this is generally a namespace string.
   group

   ;; Required - An exact value to search against.
   value

   ;; Optional - Performs pattern search on the value field. Nil defaults to false.
   pattern?])


;; Condition for searching against a given additional attribute with a value within the given range.
;; One of min-value or max-value must be present.
(defrecord AttributeRangeCondition
  [
   ;; Required - The type of the attribute being searched against (float, datetime, string, etc)
   type

   ;; Required - The name of the additional attribute being searched against.
   name

   ;; Optional - UMM Group field. In DIF9 and DIF10 metadata this is generally a namespace string.
   group

   ;; Optional - Search for values for an attribute which are greater than the min-value.
   min-value

   ;; Optional - Search for values for an attribute which are less than the max-value.
   max-value

   ;; Optional - If set to true the search is performed with < max-value and > min-value, otherwise
   ;; <= max-value and >= min-value are used. Nil defaults to false.
   exclusive?])

;; The HasGranulesCondition type represents a condition that restricts a query to collection that
;; are known to have granules.
(defrecord HasGranulesCondition [has-granules])

(defmethod common-qm/default-sort-keys :granule
  [_]
  [{:field :provider-id :order :asc}
   {:field :start-date :order :asc}])

(defmethod common-qm/default-sort-keys :tag
  [_]
  [{:field :tag-key :order :asc}])

(defmethod common-qm/default-sort-keys :variable
  [_]
  [{:field :variable-name :order :asc}
   {:field :provider-id :order :asc}])

(defmethod common-qm/default-sort-keys :service
  [_]
  [{:field :service-name :order :asc}
   {:field :provider-id :order :asc}])

(defmethod common-qm/default-sort-keys :collection
  [_]
  [{:field :entry-title :order :asc}
   {:field :provider-id :order :asc}])

(defmethod common-qm/concept-type->default-query-attribs :granule
  [_]
  {:condition (common-qm/->MatchAllCondition)
   :page-size common-qm/default-page-size
   :offset common-qm/default-offset
   :result-format :xml
   :echo-compatible? false
   :all-revisions? false})

(defmethod common-qm/concept-type->default-query-attribs :tag
  [_]
  {:condition (common-qm/->MatchAllCondition)
   :page-size common-qm/default-page-size
   :offset common-qm/default-offset
   :result-format :json
   :echo-compatible? false
   :all-revisions? false})

(defmethod common-qm/concept-type->default-query-attribs :variable
  [_]
  {:condition (common-qm/->MatchAllCondition)
   :page-size common-qm/default-page-size
   :offset common-qm/default-offset
   :result-format :json
   :echo-compatible? false
   :all-revisions? false})

(defmethod common-qm/concept-type->default-query-attribs :service
  [_]
  {:condition (common-qm/->MatchAllCondition)
   :page-size common-qm/default-page-size
   :offset common-qm/default-offset
   :result-format :json
   :echo-compatible? false
   :all-revisions? false})

(defmethod common-qm/concept-type->default-query-attribs :collection
  [_]
  {:condition (common-qm/->MatchAllCondition)
   :page-size common-qm/default-page-size
   :offset common-qm/default-offset
   :result-format :xml
   :echo-compatible? false
   :all-revisions? false})

;; Enable pretty printing of records
(record-pretty-printer/enable-record-pretty-printing
 SpatialCondition
 TemporalCondition
 OrbitNumberValueCondition
 OrbitNumberRangeCondition
 EquatorCrossingLongitudeValueCondition
 EquatorCrossingLongitudeRangeCondition
 CoordinateValueCondition
 CoordinateRangeCondition
 TwoDCoordinateCondition
 TwoDCoordinateSystemCondition
 EquatorCrossingDateCondition
 CollectionQueryCondition
 AttributeNameAndGroupCondition
 AttributeValueCondition
 AttributeRangeCondition)

;; Change this as part of CMR-1329.
(defn normalize-score
  "The score is divided by 2 to mimic the Catalog REST logic that tries to keep the boosts normalized
  between 0.0 and 1.0. That doesn't actually work but it at least matches Catalog REST's style. As
  of this writing there are plans to improve the relevancesort algorithm to better match client's
  expectations of better results. We will wait until that time to come up with a more reasonable
  approach."
  [score]
  (when score (/ score 2.0)))
