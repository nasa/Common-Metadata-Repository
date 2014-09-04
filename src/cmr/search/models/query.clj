(ns cmr.search.models.query
  "Defines various query models and conditions."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.parameter-parser :as pp]
            [clojure.string :as s]))

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

   ;; flag to determine if the results should be pretty printed in the response
   pretty?

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

   ;; The minimum value (inclusive)
   min-value

   ;; Them maximum value (inclusive)
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
   ;; The field being searched.
   field

   ;; The date range condition
   date-range-condition

   ;; The start-day value
   start-day

   ;; The end-day value
   end-day
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

(defrecord EquatorCrossingLongitudeCondition
  [
   min-value
   max-value
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
             :pretty? false}
   :collection {:condition (->MatchAllCondition)
                :page-size default-page-size
                :page-num default-page-num
                :sort-keys (default-sort-keys :collection)
                :result-format :xml
                :pretty? false}})

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
  [field min max]
  (map->NumericRangeCondition {:field field
                               :min-value min
                               :max-value max}))

(defn string-range-condition
  "Create a string range condition."
  [field start stop]
  (map->StringRangeCondition {:field field :start-value start :end-value stop}))

(defn date-range-condition
  "Creates a DateRangeCondition."
  [field start stop]
  (map->DateRangeCondition {:field field
                            :start-date start
                            :end-date stop}))
(defn date-value-condition
  "Creates a DateValueCondtion."
  [field value]
  (->DateValueCondition field value))

(defn nested-condition
  "Creates a nested condition."
  [path condition]
  (->NestedCondition path condition))

(defn group-conds
  "Combines the conditions together in the specified type of group."
  [type conditions]
  (cond
    (empty? conditions) (errors/internal-error! "Grouping empty list of conditions")
    (= (count conditions) 1) (first conditions)
    ;; Performance enhancement: If subconditions are condition groups of the same type they can
    ;; be combined into this new one.
    :else (->ConditionGroup type conditions)))

(defn and-conds
  "Combines conditions in an AND condition."
  [conditions]
  (group-conds :and conditions))

(defn or-conds
  "Combines conditions in an OR condition."
  [conditions]
  (group-conds :or conditions))

(defn text-condition
  [field query-str]
  (->TextCondition field query-str))

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
     (group-conds group-operation
                  (map #(->StringCondition field % case-sensitive? pattern?)
                       values))
     :else
     ;; Strings condition can be used non-pattern, strings combined as an OR
     (->StringsCondition field values case-sensitive?))))

(defn numeric-range-str->condition
  "Creates a numeric range condition."
  [field value]
  (let [{:keys [min-value max-value]} (pp/numeric-range-parameter->map value)]
    (->NumericRangeCondition field min-value max-value)))
