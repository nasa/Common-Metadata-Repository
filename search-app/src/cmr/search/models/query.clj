(ns cmr.search.models.query
  "Defines various query models and conditions."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.parameter-parser :as pp]
            [clojure.string :as s]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(def default-page-size 10)
(def default-page-num 1)

(defrecord Query
  [
   ;; the concept type that is being queried.
   concept-type

   ;; the root level condition
   condition

   ;; the desired number of results
   page-size

   ;; the desired page in the result set - starting at zero
   page-num

   ;; a sequence of maps with :order and :field for sorting
   sort-keys

   ;; desired result format
   result-format

   ;; A list of features identified by symbols that can be enabled or disabled.
   result-features

   ;; Flag indicates if results should be returned in a way that is ECHO compatible.
   echo-compatible?

   ;; Keywords are included at the top level of the query so they can be used to construct the final
   ;; resulting function_score query filters. The keyword condition uses these to construct
   ;; the full text search on the :keyword field, but they are also needed for the filter sections
   ;; that compute the score. Keeping them here is cleaner than having to search for the
   ;; keyword condition and pull them from there.
   keywords

   ;; Aggregations to send to elastic. The format of this object matches the shape expected by
   ;; elastisch for aggregations. It should be a map of aggregation names to aggregation types
   ;; with details. See the elastisch documentation for more information (which is somewhat light
   ;; aggregation documentatation unfortunately)
   aggregations

   ;; Flag to allow acls to be bypassed. For internal use only
   skip-acls?
   ])

(defrecord ConditionGroup
  [
   ;; The operation combining the conditions i.e. :and or :or
   operation

   ;; A sequence of conditions in the group
   conditions
   ])

(defrecord NestedCondition
  [
   ;; The path for the nested query
   path

   ;; The nested condition
   condition
   ])

;; whitespace analyzed text query
(defrecord TextCondition
  [
   ;; the field being searched
   field

   ;; the query string
   query-str
   ])

(defrecord StringCondition
  [
   ;; The field being searched.
   field

   ;; The value to match
   value

   ;; indicates if the search is case sensitive. Defaults to false.
   case-sensitive?

   ;; Indicates if the search contains pattern matching expressions. Defaults to false.
   pattern?
   ])

;; Represents a search for multiple possible values on a single field. The values are essentially OR'd
(defrecord StringsCondition
  [
   ;; The field being searched.
   field

   ;; The values to match
   values

   ;; indicates if the search is case sensitive. Defaults to false.
   case-sensitive?
   ])

(defrecord NegatedCondition
  [
   ;; condition to exclude
   condition
   ])

(defrecord BooleanCondition
  [
   ;; The field being searched.
   field

   ;; The boolean value to match
   value
   ])

(defrecord SpatialCondition
  [
   ;; One of cmr.spatial polygon, line, point, or mbr
   shape
   ])

;; Allows execution of a custom native search script
(defrecord ScriptCondition
  [
   ;; name of the script
   script

   ;; Parameter map of names to values
   params
   ])

;; ExistCondition represents the specified field must have value, i.e. filed is not null
(defrecord ExistCondition
  [
   ;; The field being searched.
   field
   ])

;; MissingCondition represents the specified field must not have value, i.e. filed is nil
(defrecord MissingCondition
  [
   ;; The field being searched.
   field
   ])

(defrecord DateValueCondition
  [
   ;; The field being searched
   field

   ;; The date value
   value
   ])

(defrecord DateRangeCondition
  [
   ;; The field being searched.
   field

   ;; The start-date value
   start-date

   ;; The end-date value
   end-date

   ;; If true, exclude the boundary values. Default to false.
   exclusive?
   ])

(defrecord NumericValueCondition
  [
   ;; The field being searched
   field

   ;; The value to match.
   value
   ])

(defrecord NumericRangeCondition
  [
   ;; The field being searched.
   field

   ;; The minimum value
   min-value

   ;; Them maximum value
   max-value

   ;; If true, exclude the boundary values. Default to false.
   exclusive?
   ])

;; This condition can be used for finding concepts having two fields representing a range of values
;; where that range overlaps a given range.
(defrecord NumericRangeIntersectionCondition
  [
   ;; The field representing the minimum value of an indexed range
   min-field

   ;; The field representing the maximum value of an indexed range
   max-field

   ;; The minimum value of the search range (inclusive)
   min-value

   ;; The maximum value of the search range (inclusive)
   max-value
   ])

(defrecord StringRangeCondition
  [
   ;; The field being searched
   field

   ;; The start value for the range
   start-value

   ;; The end value for the range
   end-value
   ])

(defrecord TemporalCondition
  [
   start-date
   end-date
   start-day
   end-day
   exclusive?
   ])

(defrecord OrbitNumberValueCondition
  [
   value
   ])

(defrecord OrbitNumberRangeCondition
  [
   min-value
   max-value
   ])

(defrecord EquatorCrossingLongitudeValueCondition
  [
   value
   ])

(defrecord EquatorCrossingLongitudeRangeCondition
  [
   min-value
   max-value
   ])

;; This condition is used for holding two-d-coordinate value
(defrecord CoordinateValueCondition
  [
   value
   ])

;; This condition is used for holding two-d-coordinate range
(defrecord CoordinateRangeCondition
  [
   min-value
   max-value
   ])

;; This condition is used for holding two-d-coordinate-system coordinates info
(defrecord TwoDCoordinateCondition
  [
   ;; it is nil, CoordinateValueCondition or CoordinateRangeCondition
   coordinate-1-cond
   ;; it is nil, CoordinateValueCondition or CoordinateRangeCondition
   coordinate-2-cond
   ])

(defrecord TwoDCoordinateSystemCondition
  [
   two-d-name
   ;; it is nil or a list of TwoDCoordinateConditions
   two-d-conditions
   ;; indicates if the search is case sensitive. Defaults to false.
   case-sensitive?
   ])

(defrecord EquatorCrossingDateCondition
  [
   start-date
   end-date
   ])

(defrecord CollectionQueryCondition
  [
   ;; The condition to find collections
   condition
   ])

(defrecord MatchAllCondition
  [])

(defrecord MatchNoneCondition
  [])

(def attribute-types
  "A list of valid additional attribute search types"
  [:float :int :string :date :time :datetime])

(defrecord AttributeNameCondition
  [
   name
   pattern?
   ])

(defrecord AttributeValueCondition
  [
   type
   name
   value
   pattern?
   ])

(defrecord AttributeRangeCondition
  [
   type
   name
   min-value
   max-value
   exclusive? ;; if true, exclude the boundary values
   ])

(def default-sort-keys
  {:granule [{:field :provider-id :order :asc}
             {:field :start-date :order :asc}]
   :collection [{:field :entry-title :order :asc}
                {:field :provider-id :order :asc}]})

(def concept-type->default-query-attribs
  {:granule {:condition (->MatchAllCondition)
             :page-size default-page-size
             :page-num default-page-num
             :sort-keys (default-sort-keys :granule)
             :result-format :xml
             :echo-compatible? false}
   :collection {:condition (->MatchAllCondition)
                :page-size default-page-size
                :page-num default-page-num
                :sort-keys (default-sort-keys :collection)
                :result-format :xml
                :echo-compatible? false}})

(defn query
  "Constructs a query with the given type, page-size, page-num, result-format,
  and root condition. If root condition is not provided it matches everything.
  If page-size, page-num, or result-format are not specified then they are given default values."
  [attribs]
  (let [concept-type (:concept-type attribs)]
    (map->Query (merge-with (fn [default-v v]
                              (if (some? v) v default-v))
                            (concept-type->default-query-attribs concept-type) attribs))))

(defn numeric-value-condition
  "Creates a NumericValueCondition"
  [field value]
  (map->NumericValueCondition {:field field :value value}))

(defn numeric-range-condition
  ([field min max]
   (numeric-range-condition field min max false))
  ([field min max exclusive?]
   (map->NumericRangeCondition {:field field
                                :min-value min
                                :max-value max
                                :exclusive? exclusive?})))

(defn numeric-range-intersection-condition
  [min-field max-field min max]
  (map->NumericRangeIntersectionCondition {:min-field min-field
                                           :max-field max-field
                                           :min-value min
                                           :max-value max}))

(defn string-range-condition
  "Create a string range condition."
  [field start stop]
  (map->StringRangeCondition {:field field :start-value start :end-value stop}))

(defn date-range-condition
  "Creates a DateRangeCondition."
  ([field start stop]
   (date-range-condition field start stop false))
  ([field start stop exclusive?]
   (map->DateRangeCondition {:field field
                             :start-date start
                             :end-date stop
                             :exclusive? exclusive?})))
(defn date-value-condition
  "Creates a DateValueCondtion."
  [field value]
  (->DateValueCondition field value))

(defn nested-condition
  "Creates a nested condition."
  [path condition]
  (->NestedCondition path condition))

(def match-none
  (->MatchNoneCondition))

(def match-all
  (->MatchAllCondition))

(defn text-condition
  [field query-str]
  (->TextCondition field query-str))

(defn negated-condition
  "Creates a negated condition."
  [value]
  (->NegatedCondition value))

(defn string-condition
  ([field value]
   (string-condition field value false false))
  ([field value case-sensitive? pattern?]
   (when-not value
     (errors/internal-error! (str "Null value for field: " field)))
   (->StringCondition field value case-sensitive? pattern?)))

(defn string-conditions
  "Creates a string condition."
  ([field values]
   (string-conditions field values false false :or))
  ([field values case-sensitive?]
   (string-conditions field values case-sensitive? false :or))
  ([field values case-sensitive? pattern? group-operation]
   (when-not (seq values)
     (errors/internal-error! (str "Null or empty values for field: " field)))
   (cond
     (= (count values) 1)
     (string-condition field (first values) case-sensitive? pattern?)

     (or pattern? (= group-operation :and))
     (->ConditionGroup group-operation
                       (map #(string-condition field % case-sensitive? pattern?)
                            values))
     :else
     ;; Strings condition can be used non-pattern, strings combined as an OR
     (->StringsCondition field values case-sensitive?))))

(defn numeric-range-str->condition
  "Creates a numeric range condition."
  [field value]
  (let [{:keys [min-value max-value]} (pp/numeric-range-parameter->map value)]
    (map->NumericRangeCondition {:field field
                                 :min-value min-value
                                 :max-value max-value})))


;; Enable pretty printing of records
(record-pretty-printer/enable-record-pretty-printing
  Query
  ConditionGroup
  NestedCondition
  TextCondition
  StringCondition
  StringsCondition
  NegatedCondition
  BooleanCondition
  SpatialCondition
  ScriptCondition
  ExistCondition
  MissingCondition
  DateValueCondition
  DateRangeCondition
  NumericValueCondition
  NumericRangeCondition
  NumericRangeIntersectionCondition
  StringRangeCondition
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
  MatchAllCondition
  MatchNoneCondition
  AttributeNameCondition
  AttributeValueCondition
  AttributeRangeCondition)

