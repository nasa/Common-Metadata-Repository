(ns cmr.umm-spec.validation.additional-attribute
  "Defines validations for UMM collection product specific attribute."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]
            [cmr.common.util :as util]
            [cmr.umm-spec.models.common :as common]
            [cmr.umm-spec.additional-attribute :as aa]))

(def no-range-data-types
  "Set of data-types of additional attribute that do not support parameter range"
  #{:string :boolean})

(def validate-range-data-types
  "Set of data-types of additional attribute that require parameter range validations"
  #{:int :float :datetime :date :time})

(defn- field-value-validation
  "Validates the given additional attribute field value matches the data type"
  [data-type field value]
  (try
    (aa/parse-value (aa/gen-data-type data-type) value)
    nil
    (catch Exception _
      [(format "%s [%s] is not a valid value for type [%s]."
               (v/humanize-field field) value (aa/gen-data-type data-type))])))

(defn- values-match-data-type-validation
  "Validates additional attribute values match the data type"
  [field-path aa]
  (let [{:keys [DataType]} aa
        errors (->> (select-keys aa [:ParameterRangeBegin :ParameterRangeEnd :Value])
                    (mapcat #(apply field-value-validation DataType %))
                    (remove nil?))]
    (when (seq errors)
      {field-path errors})))

(defn- range-type-validation
  "Validates data type of the given additional attribute supports range if range value is present"
  [aa]
  (let [{:keys [DataType]} aa
        value-fn (fn [field value]
                   (when value
                     [(format "%s is not allowed for type [%s]"
                              (v/humanize-field field) (aa/gen-data-type DataType))]))]
    (when (no-range-data-types DataType)
      (->> (select-keys aa [:ParameterRangeBegin :ParameterRangeEnd])
           (mapcat #(apply value-fn %))
           (remove nil?)))))

(defn- parameter-range-validation
  "Validates additional attribute parameter range related rules"
  [field-path aa]
  (let [errors (range-type-validation aa)]
    (when (seq errors)
      {field-path errors})))

(def aa-validations
  "Defines the additional attribute validations for collections"
  [values-match-data-type-validation
   parameter-range-validation])
