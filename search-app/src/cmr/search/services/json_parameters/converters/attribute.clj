(ns cmr.search.services.json-parameters.converters.attribute
  "Contains functions for parsing and validating JSON query attribute conditions."
  (:require [clojure.set :as set]
            [cmr.common.date-time-parser :as parser]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.search.services.json-parameters.conversion :as jp]
            [cmr.search.services.messages.attribute-messages :as am]
            [cmr.search.services.parameters.converters.attribute :as p-attr]))

(def ^:private attribute-type->parser-fn
  "A map of attribute types to functions that can parse a value"
  {:datetime parser/parse-datetime
   :time parser/parse-time
   :date parser/parse-date
   :float float
   :int int
   :string str})

(defn- type-validation
  "Validates type within the provided condition. Returns a list of errors if validation fails."
  [{:keys [type name value min-value max-value]}]
  (when type
    (remove nil?
            (concat (when-not name
                       [am/type-requires-name-msg])
                    (when-not (or (some? value) (some? min-value) (some? max-value))
                       [am/type-requires-value-msg])))))

(defn- value-validation
  "Validates value, min_value, or max_value are present when type is present within the provided
  condition. Returns a list of errors if validation fails."
  [{:keys [value min-value max-value type]}]
  (when (or (some? value) (some? min-value) (some? max-value))
    (when (nil? type)
      [am/value-requires-type-msg])))

(defn- range-values-validation
  "Validates min_value and max_value within the provided condition. Returns a list of errors if
  validation fails."
  [{:keys [value min-value max-value]}]
  (when (some? value)
    (when (or (some? min-value) (some? max-value))
      [am/conflicting-value-and-range-msg])))

(defn- exclude-boundary-validation
  "Validates exclude-boundary within the provided condition. Returns a list of errors if validation
  fails."
  [{:keys [exclude-boundary min-value max-value]}]
  (when (some? exclude-boundary)
    (when (and (nil? min-value) (nil? max-value))
      [am/invalid-exclude-boundary-msg])))

(defn- pattern-validation
  "Validates pattern within the provided condition. Returns a list of errors if validation fails."
  [{:keys [pattern type]}]
  (when (some? pattern)
    (when (some? type)
      [am/invalid-pattern-msg])))

(def ^:private attribute-validation-fns
  "A list of the functions that can validate attribute conditions. They all accept the condition
  as an argument and return a list of errors."
  [type-validation
   value-validation
   range-values-validation
   exclude-boundary-validation
   pattern-validation])

(defn- validate-attribute-condition
  "Custom validation to validate an additional attribute condition. Throws a bad request error if
  validation fails. Returns the condition to be chained in other calls."
  [condition]
  (let [type (keyword (:type condition))
        parser-fn (attribute-type->parser-fn type)
        errors (mapcat #(% condition) attribute-validation-fns)
        condition (-> condition
                      (p-attr/parse-field :value parser-fn type)
                      (p-attr/parse-field :min-value parser-fn type)
                      (p-attr/parse-field :max-value parser-fn type))
        errors (remove nil? (into errors (:errors condition)))]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors))
    condition))

(defmethod jp/parse-json-condition :attribute
  [_ value]
  (let [condition (-> value
                      validate-attribute-condition
                      (update-in [:type] keyword)
                      (set/rename-keys {:exclude-boundary :exclusive?
                                        :pattern :pattern?}))]
    (cond
      ;; Range search
      (or (some? (:min-value condition)) (some? (:max-value condition)))
      (qm/map->AttributeRangeCondition condition)

      ;; Exact value search
      (some? (:value condition))
      (qm/map->AttributeValueCondition condition)

      ;; Attribute name search
      (some? (:name condition))
      (qm/map->AttributeNameCondition condition)

      ;; Attribute group search
      (some? (:group condition))
      (qm/map->AttributeGroupCondition condition)

      ;; Validation should have caught any other case
      :else
      (errors/internal-error!
        (format "Unhandled additional attribute condition %s" value)))))
