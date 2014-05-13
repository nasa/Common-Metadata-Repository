(ns cmr.search.data.complex-to-simple-converters.attribute
  "Defines functions that implement the reduce-query methods of the ComplexQueryToSimple
  protocol for product specific attribute search fields."
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.data.complex-to-simple :as c2s]
            [cmr.search.models.query :as qm]
            [cmr.search.data.datetime-helper :as h]))

(defmulti value-condition->value-filter
  "Converts an additional attribute value condition into the nested filter to use."
  (fn [condition]
    (:type condition)))

(defn type->field-name
  "Converts the attribute type into the field name that will hold the value"
  [type]
  (str (name type) "-value"))

(defmethod value-condition->value-filter :default
  [{:keys [type value]}]
  (qm/term-condition (type->field-name type) value))

(defn- date-value-condition->value-filter
  "Helper function for any date related attribute fields"
  [{:keys [type value]}]
  (qm/term-condition (type->field-name type) (h/utc-time->elastic-time value)))

(defmethod value-condition->value-filter :datetime
  [condition]
  (date-value-condition->value-filter condition))

(defmethod value-condition->value-filter :time
  [condition]
  (date-value-condition->value-filter condition))

(defmethod value-condition->value-filter :date
  [condition]
  (date-value-condition->value-filter condition))

(defmulti range-condition->range-filter
  "Converts an additional attribute range condition into the nested filter to use."
  (fn [condition]
    (:type condition)))

(defmethod range-condition->range-filter :string
  [{:keys [min-value max-value]}]
  (qm/range-condition :string-value min-value max-value))

(defmethod range-condition->range-filter :float
  [{:keys [min-value max-value]}]
  (qm/numeric-range :float-value min-value max-value))

(defmethod range-condition->range-filter :int
  [{:keys [min-value max-value]}]
  (qm/numeric-range :int-value min-value max-value))

(defn date-range-condition->range-filter
  "Helper for converting date range attribute conditions into filters"
  [{:keys [type min-value max-value]}]
  (qm/date-range-condition (type->field-name type) min-value max-value))
(defmethod range-condition->range-filter :datetime
  [condition]
  (date-range-condition->range-filter condition))

(defmethod range-condition->range-filter :time
  [condition]
  (date-range-condition->range-filter condition))

(defmethod range-condition->range-filter :date
  [condition]
  (date-range-condition->range-filter condition))


(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.AttributeValueCondition
  (c2s/reduce-query
    [condition]
    (let [value-filter (value-condition->value-filter condition)
          attrib-name (:name condition)
          term-cond (qm/map->NumericValueCondition {:field :name :value attrib-name})
          and-cond (qm/and-conds [term-cond value-filter])]
      (qm/nested-condition :attributes and-cond)))

  cmr.search.models.query.AttributeRangeCondition
  (c2s/reduce-query
    [condition]
    (let [range-filter (range-condition->range-filter condition)
          attrib-name (:name condition)
          term-cond (qm/map->NumericValueCondition {:field :name :value attrib-name})
          and-cond (qm/and-conds [term-cond range-filter])]
      (qm/nested-condition :attributes and-cond))))
