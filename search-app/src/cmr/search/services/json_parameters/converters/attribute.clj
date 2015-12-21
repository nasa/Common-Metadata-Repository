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
   :date parser/parse-datetime
   :float float
   :int int
   :string str})

(defn name-and-group-validation
  "Validates that one of name or group is present in the condition. Returns the condition so that
  the validation can be chained with other calls. Sets errors on condition if validation fails."
  [{:keys [name group] :as condition}]
  (if (and (nil? name) (nil? group))
    (update-in condition [:errors]
               conj am/missing-name-and-group)
    condition))

(defn- validate-attribute-condition
  "Custom validation to validate an additional attribute condition. Throws a bad request error if
  validation fails. Returns the condition to be chained in other calls."
  [condition]
  (let [type (keyword (:type condition))
        parser-fn (attribute-type->parser-fn type)
        condition (-> condition
                      name-and-group-validation
                      (p-attr/parse-field :value parser-fn type)
                      (p-attr/parse-field :min-value parser-fn type)
                      (p-attr/parse-field :max-value parser-fn type))
        errors (:errors condition)]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors))
    condition))

(defmethod jp/parse-json-condition :additional-attribute
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

      ;; Attribute name or group search
      (some? (or (:name condition) (:group condition)))
      (qm/map->AttributeNameAndGroupCondition condition)

      ;; Validation should have caught any other case
      :else
      (errors/internal-error!
        (format "Unhandled additional attribute condition %s" value)))))
