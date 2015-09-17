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
  (str "attributes." (name type) "-value"))

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
  [{:keys [type min-value max-value exclusive?]}]
  (qm/string-range-condition (type->field-name type) min-value max-value exclusive?))

(defmethod range-condition->range-filter :float
  [{:keys [type min-value max-value exclusive?]}]
  (qm/numeric-range-condition (type->field-name type) min-value max-value exclusive?))

(defmethod range-condition->range-filter :int
  [{:keys [type min-value max-value exclusive?]}]
  (qm/numeric-range-condition (type->field-name type) min-value max-value exclusive?))

(defn date-range-condition->range-filter
  "Helper for converting date range attribute conditions into filters"
  [{:keys [type min-value max-value exclusive?]}]
  (qm/date-range-condition (type->field-name type) min-value max-value exclusive?))

(defmethod range-condition->range-filter :datetime
  [condition]
  (date-range-condition->range-filter condition))

(defmethod range-condition->range-filter :time
  [condition]
  (date-range-condition->range-filter condition))

(defmethod range-condition->range-filter :date
  [condition]
  (date-range-condition->range-filter condition))

(defn- name-and-group-condition
  "Constructs a query condition based on the provided name and group. Name must be non-nil, but
  group can be nil."
  ([attrib-name group]
   (name-and-group-condition attrib-name group false))
  ([attrib-name group pattern?]
   (if group
     (gc/and-conds
       [(qm/string-condition :attributes.group group true pattern?)
        (qm/string-condition :attributes.name attrib-name true pattern?)])
     (qm/string-condition :attributes.name attrib-name true pattern?))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.AttributeGroupCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [{:keys [group pattern?]} condition
          group-cond (qm/string-condition :attributes.group group true pattern?)]
      (qm/nested-condition :attributes group-cond)))

  cmr.search.models.query.AttributeNameCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [{:keys [name group pattern?]} condition
          name-and-group-cond (name-and-group-condition name group pattern?)]
      (qm/nested-condition :attributes name-and-group-cond)))

  cmr.search.models.query.AttributeValueCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [{:keys [name group]} condition
          value-filter (value-condition->value-filter condition)
          name-and-group-cond (name-and-group-condition name group)
          and-cond (gc/and-conds [name-and-group-cond value-filter])]
      (qm/nested-condition :attributes and-cond)))

  cmr.search.models.query.AttributeRangeCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [{:keys [name group]} condition
          range-filter (range-condition->range-filter condition)
          name-and-group-cond (name-and-group-condition name group)
          and-cond (gc/and-conds [name-and-group-cond range-filter])]
      (qm/nested-condition :attributes and-cond))))

