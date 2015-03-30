(ns cmr.search.data.complex-to-simple-converters.attribute
  "Defines functions that implement the reduce-query-condition method of the ComplexQueryToSimple
  protocol for product specific attribute search fields."
  (:require [clojure.string :as s]
            [cmr.search.data.complex-to-simple :as c2s]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
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
  (qm/string-condition (type->field-name type) value))

(defmethod value-condition->value-filter :float
  [{:keys [type value]}]
  (qm/numeric-value-condition (type->field-name type) value))

(defmethod value-condition->value-filter :int
  [{:keys [type value]}]
  (qm/numeric-value-condition (type->field-name type) value))

(defmethod value-condition->value-filter :string
  [{:keys [type value pattern?]}]
  (qm/string-condition (type->field-name type) value false pattern?))

(defn- date-value-condition->value-filter
  "Helper function for any date related attribute fields"
  [{:keys [type value]}]
  (qm/date-value-condition (type->field-name type) value))

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
  (qm/string-range-condition :string-value min-value max-value))

(defmethod range-condition->range-filter :float
  [{:keys [min-value max-value]}]
  (qm/numeric-range-condition :float-value min-value max-value))

(defmethod range-condition->range-filter :int
  [{:keys [min-value max-value]}]
  (qm/numeric-range-condition :int-value min-value max-value))

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
  cmr.search.models.query.AttributeNameCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [{:keys [name pattern?]} condition
          name-cond (qm/string-condition :name name true pattern?)]
      (qm/nested-condition :attributes name-cond))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.GranuleAttributeNameCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [name-cond (qm/map->NumericValueCondition {:field :name :value (:name condition)})]
      (qm/nested-condition :attributes name-cond))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.AttributeValueCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [value-filter (value-condition->value-filter condition)
          attrib-name (:name condition)
          name-cond (qm/map->NumericValueCondition {:field :name :value attrib-name})
          and-cond (gc/and-conds [name-cond value-filter])]
      (qm/nested-condition :attributes and-cond)))

  cmr.search.models.query.AttributeRangeCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [range-filter (range-condition->range-filter condition)
          attrib-name (:name condition)
          name-cond (qm/map->NumericValueCondition {:field :name :value attrib-name})
          and-cond (gc/and-conds [name-cond range-filter])]
      (qm/nested-condition :attributes and-cond))))
