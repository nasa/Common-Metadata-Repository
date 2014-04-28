(ns cmr.search.data.query-to-elastic-converters.attribute
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.data.query-to-elastic :as q2e]
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
  {:term {(type->field-name type) value}})

(defn- date-value-condition->value-filter
  "Helper function for any date related attribute fields"
  [{:keys [type value]}]
  {:term {(type->field-name type) (h/utc-time->elastic-time value)}})

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
  (let [r {:gte (or min-value "")}
        r (if max-value (assoc r :lte max-value) r)]
    {:range {:string-value r}}))

(defmethod range-condition->range-filter :float
  [{:keys [min-value max-value]}]
  (let [r {:gte (or min-value (* -1 Float/MAX_VALUE))}
        r (if max-value (assoc r :lte max-value) r)]
    {:range {:float-value r
             :execution "fielddata"}}))

(defmethod range-condition->range-filter :int
  [{:keys [min-value max-value]}]
  (let [r {:gte (or min-value Integer/MIN_VALUE)}
        r (if max-value (assoc r :lte max-value) r)]
    {:range {:int-value r
             :execution "fielddata"}}))

(defn date-range-condition->range-filter
  "Helper for converting date range attribute conditions into filters"
  [{:keys [type min-value max-value]}]
  (let [r {:gte (if min-value
                  (h/utc-time->elastic-time min-value)
                  h/earliest-echo-start-date)}
        r (if max-value
            (assoc r :lte (h/utc-time->elastic-time max-value))
            r)]
    {:range {(type->field-name type) r
             :execution "fielddata"}}))

(defmethod range-condition->range-filter :datetime
  [condition]
  (date-range-condition->range-filter condition))

(defmethod range-condition->range-filter :time
  [condition]
  (date-range-condition->range-filter condition))

(defmethod range-condition->range-filter :date
  [condition]
  (date-range-condition->range-filter condition))


(extend-protocol q2e/ConditionToElastic
  cmr.search.models.query.AttributeValueCondition
  (condition->elastic
    [condition]
    (let [value-filter (value-condition->value-filter condition)
          attrib-name (:name condition)]
      {:nested {:path "attributes"
                :filter {:and {:filters [{:term {:name attrib-name}}
                                         value-filter]}}}}))

  cmr.search.models.query.AttributeRangeCondition
  (condition->elastic
    [condition]
    (let [range-filter (range-condition->range-filter condition)
          attrib-name (:name condition)]
      {:nested {:path "attributes"
                :filter {:and {:filters [{:term {:name attrib-name}}
                                         range-filter]}}}}))


  )