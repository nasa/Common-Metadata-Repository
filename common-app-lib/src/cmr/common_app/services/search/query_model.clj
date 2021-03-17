(ns cmr.common-app.services.search.query-model
  "Defines various query models and conditions."
  (:require
    [clojure.string :as s]
    [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
    [cmr.common.parameter-parser :as pp]
    [cmr.common.services.errors :as errors]))

(def default-page-size 10)

(def default-page-num 1)

(def default-offset 0)

(defrecord Query
  [
   ;; the concept type that is being queried.
   concept-type

   ;; the root level condition
   condition

   ;; the desired number of results
   page-size

   ;; the desired offset, equivalent to Elasticsearch's "from"
   offset

   ;; a sequence of maps with :order and :field for sorting
   sort-keys

   ;; desired result format in keyword
   result-format

   ;; A list of features identified by symbols that can be enabled or disabled.
   result-features

   ;; Aggregations to send to elastic. The format of this object matches the shape expected by
   ;; elastisch for aggregations. It should be a map of aggregation names to aggregation types
   ;; with details. See the elastisch documentation for more information (which is somewhat light
   ;; aggregation documentatation unfortunately)
   aggregations

   ;; highlights to send to elasticsearch. The format of this object matches the shape expected by
   ;; elastisch for highlights.
   highlights

   ;; Flag to allow acls to be bypassed. For internal use only
   skip-acls?

   ;; Options for the search response.
   result-options])


(defrecord ConditionGroup
  [
   ;; The operation combining the conditions i.e. :and or :or
   operation

   ;; A sequence of conditions in the group
   conditions])


(defrecord NestedCondition
  [
   ;; The path for the nested query
   path

   ;; The nested condition
   condition])


;; whitespace analyzed text query
(defrecord TextCondition
  [
   ;; the field being searched
   field

   ;; the query string
   query-str])


(defrecord StringCondition
  [
   ;; The field being searched.
   field

   ;; The value to match
   value

   ;; indicates if the search is case sensitive. Defaults to false.
   case-sensitive?

   ;; Indicates if the search contains pattern matching expressions. Defaults to false.
   pattern?])


;; Represents a search for multiple possible values on a single field. The values are essentially OR'd
(defrecord StringsCondition
  [
   ;; The field being searched.
   field

   ;; The values to match
   values

   ;; indicates if the search is case sensitive. Defaults to false.
   case-sensitive?])


(defrecord NegatedCondition
  [
   ;; condition to exclude
   condition])


(defrecord BooleanCondition
  [
   ;; The field being searched.
   field

   ;; The boolean value to match
   value])

;; Allows execution of a custom native search script
(defrecord ScriptCondition
  [
   ;; name of the script source
   source

   ;; lang of the script
   lang

   ;; Parameter map of names to values
   params])


;; ExistCondition represents the specified field must have value, i.e. filed is not null
(defrecord ExistCondition
  [
   ;; The field being searched.
   field])


;; MissingCondition represents the specified field must not have value, i.e. filed is nil
(defrecord MissingCondition
  [
   ;; The field being searched.
   field])


(defrecord DateValueCondition
  [
   ;; The field being searched
   field

   ;; The date value
   value])


(defrecord DateRangeCondition
  [
   ;; The field being searched.
   field

   ;; The start-date value
   start-date

   ;; The end-date value
   end-date

   ;; If true, exclude the boundary values. Default to false.
   exclusive?])


(defrecord NumericValueCondition
  [
   ;; The field being searched
   field

   ;; The value to match.
   value])


(defrecord NumericRangeCondition
  [
   ;; The field being searched.
   field

   ;; The minimum value
   min-value

   ;; Them maximum value
   max-value

   ;; If true, exclude the boundary values. Default to false.
   exclusive?])


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
   max-value])


(defrecord StringRangeCondition
  [
   ;; The field being searched
   field

   ;; The start value for the range
   start-value

   ;; The end value for the range
   end-value])

(defrecord MatchAllCondition
  [])

(defrecord MatchNoneCondition
  [])

(defrecord MatchCondition
  [
    ;; The field being searched
    field

    ;; The value of the field
    value])

(defrecord MatchBoolPrefixCondition
  [
    ;; The field
    field
  
    ;; The value of the field
    value])

(defrecord MultiMatchCondition
  [
    ;; The query type, match, match_bool_prefix...
    query-type
    ;; The fields to query over
    fields
    ;; Value to query for
    value
    ;; Options relating to query-type, see ES documentation
    options
  ])

(defrecord RelatedItemQueryCondition
  [
   ;; The concept type being found by the inner condition
   concept-type
   ;; The condition that will be used to search
   condition

   ;; The fields to retrieve from the search
   result-fields

   ;; A function that will take the results found in result-field and creates a new condition to
   ;; replace the instance of the related item query condition
   results-to-condition-fn])

(defmulti default-sort-keys
  "Multimethod for returning the default sort keys for a concept type."
  (fn [concept-type]
    concept-type))

(defmethod default-sort-keys :default
  [_]
  ;; No sorting by default
  [])

(defmulti concept-type->default-query-attribs
  "Multimethod for returning the default query attributes for a given concept type."
  (fn [concept-type]
    concept-type))

(defmethod concept-type->default-query-attribs :default
  [_]
  {:condition (->MatchAllCondition)
   :page-size default-page-size
   :offset default-offset
   :result-format :json})

(defprotocol BaseResultFormat
  "Define the function to return the base result format (i.e. the keyword that describes a result
  format, not the version of the result format) from a query or result format"
  (base-result-format [x]))

(extend-protocol BaseResultFormat
  cmr.common_app.services.search.query_model.Query
  (base-result-format
    [query]
    (base-result-format (:result-format query)))

  clojure.lang.IPersistentMap
  (base-result-format
    [result-format]
    (:format result-format))

  java.lang.Object
  (base-result-format
    [result-format]
    result-format))

(defn query
  "Constructs a query with the given attributes and root condition. If root condition is not
  provided it matches everything. If page-size, offset, or result-format are not specified
  then they are given default values."
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
  ([field start stop]
   (string-range-condition field start stop false))
  ([field start stop exclusive?]
   (map->StringRangeCondition {:field field
                               :start-value start
                               :end-value stop
                               :exclusive? exclusive?})))

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

(defn match
  [field value]
  (->MatchCondition field value))

(defn match-bool-prefix
  [field value]
  (->MatchBoolPrefixCondition field value))

(defn multi-match
  ([query-type fields value]
   (multi-match query-type fields value {}))
  ([query-type fields value opts]
   (->MultiMatchCondition query-type fields value opts)))

(defn text-condition
  [field query-str]
  (->TextCondition field query-str))

(defn boolean-condition
  "Creates a boolean condition."
  [field value]
  (map->BooleanCondition {:field field :value value}))

(defn negated-condition
  "Creates a negated condition."
  [value]
  (->NegatedCondition value))

(defn exist-condition
  "Returns a query condition that checks that field exists."
  [field]
  (->ExistCondition field))

(defn not-exist-condition
  "Returns a query condition that checks that field does not exist."
  [field]
  (negated-condition (exist-condition field)))

(defn string-condition
  "Returns a string condition"
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
  ScriptCondition
  ExistCondition
  MissingCondition
  DateValueCondition
  DateRangeCondition
  NumericValueCondition
  NumericRangeCondition
  NumericRangeIntersectionCondition
  StringRangeCondition
  MatchAllCondition
  MatchNoneCondition
  MatchCondition
  MatchBoolPrefixCondition
  MultiMatchCondition
  RelatedItemQueryCondition)
