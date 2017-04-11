(ns cmr.umm-spec.validation.additional-attribute
  "Defines validations for UMM collection product specific attribute."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]
            [cmr.umm-spec.validation.umm-spec-validation-utils :as vu]
            [cmr.common.util :as util]
            [cmr.umm-spec.models.umm-common-models :as common]
            [cmr.umm-spec.additional-attribute :as aa]))

(def no-range-data-types
  "Set of data-types of additional attribute that do not support parameter range"
  #{"STRING" "BOOLEAN"})

(def validate-range-data-types
  "Set of data-types of additional attribute that require parameter range validations"
  #{"INT" "FLOAT" "DATETIME" "DATE" "TIME"})

(def valid-data-types
 "Set of valid data types of additional attributes"
 #{"STRING", "FLOAT", "INT", "BOOLEAN", "DATE", "TIME", "DATETIME", "DATE_STRING", "TIME_STRING", "DATETIME_STRING"})

(defn- data-type-validator
  "Validates data-type is one of the valid product specific attribute data types.
  NOTE: This is also handled by schema validation, but umm-spec schema validation
  errors are warnings at this time. Put here to throw error"
  [field-path value]
  (when-not (some #{value} valid-data-types)
    {field-path [(format "Additional Attribute %%s [%s] is not a valid data type."
                         (if value
                           value
                           ""))]}))

(defn- field-value-validation
  "Validates the given additional attribute field value matches the data type"
  [data-type field value]
  (try
    (aa/parse-value (aa/gen-data-type data-type) value)
    nil
    (catch Exception _
      [(vu/escape-error-string
        (format "%s [%s] is not a valid value for type [%s]."
                (v/humanize-field field) value (aa/gen-data-type data-type)))])))

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

(defn- range-values-validation
  "Validates range values"
  [attrib]
  (let [parsed-value (::aa/parsed-value attrib)
        parsed-parameter-range-begin (::aa/parsed-parameter-range-begin attrib)
        parsed-parameter-range-end (::aa/parsed-parameter-range-end attrib)
        data-type (:DataType attrib)]
    (when (validate-range-data-types data-type)
      (cond
        (and parsed-parameter-range-begin parsed-parameter-range-end
             (util/greater-than? parsed-parameter-range-begin parsed-parameter-range-end))
        [(format "Parameter Range Begin [%s] cannot be greater than Parameter Range End [%s]."
                 (:ParameterRangeBegin attrib)
                 (:ParameterRangeEnd attrib))]

        (and parsed-value parsed-parameter-range-begin
             (util/less-than? parsed-value parsed-parameter-range-begin))
        [(format "Value [%s] cannot be less than Parameter Range Begin [%s]."
                 (:Value attrib)
                 (:ParameterRangeBegin attrib))]

        (and parsed-value parsed-parameter-range-end
             (util/greater-than? parsed-value parsed-parameter-range-end))
        [(format "Value [%s] cannot be greater than Parameter Range End [%s]."
                 (:Value attrib)
                 (:ParameterRangeEnd attrib))]))))

(defn- parameter-range-validation
  "Validates additional attribute parameter range related rules"
  [field-path aa]
  (let [errors (concat (range-type-validation aa)
                       (range-values-validation aa))]
    (when (seq errors)
      {field-path errors})))

(def additional-attribute-validation
  "Defines the list of validation functions for validating additional attributes"
  [(vu/unique-by-name-validator :Name)
   (v/every [{:DataType data-type-validator}
             values-match-data-type-validation
             parameter-range-validation])])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Granules

(defn- value-refs-match-data-type-validation
  "Validates granule additional attribute ref values match the parent data type"
  [field-path aa]
  (let [data-type (get-in aa [:parent :DataType])
        errors (->> (:values aa)
                    (mapcat #(field-value-validation data-type :value %))
                    (remove nil?))]
    (when (seq errors)
      {field-path errors})))

(defn- value-refs-parameter-range-validation
  "Validates granule satisfy parent collection's additional attribute parameter range rules"
  [field-path aa]
  (let [{:keys [name values parent]} aa
        data-type (:DataType parent)
        errors (if (seq values)
                 (when parent
                   ;; This reuses the parent collection validation for the granules.
                   (for [v values
                         :let [parsed-value (aa/safe-parse-value data-type v)
                               new-aa (assoc parent
                                             :Value v
                                             ::aa/parsed-value parsed-value)]
                         error (range-values-validation new-aa)
                         :when error]
                     error))
                 [(format "%%s [%s] values must not be empty." name)])]
    (when (seq errors)
      {field-path errors})))

(def aa-ref-validations
  "Defines the additional attribute reference validations for granules"
  [value-refs-match-data-type-validation
   value-refs-parameter-range-validation])
