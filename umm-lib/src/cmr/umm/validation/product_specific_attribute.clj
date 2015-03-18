(ns cmr.umm.validation.product-specific-attribute
  "Defines validations for UMM collection product specific attribute."
  (:require [cmr.common.validations.core :as v]
            [cmr.umm.collection :as c]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(defn- data-type-validator
  "Validates data-type is one of the valid product specific attribute data types"
  [field-path value]
  (when-not (some #{value} c/product-specific-attribute-types)
    {field-path [(format "Additional Attribute %%s [%s] is not a valid data type."
                         (if value
                           (psa/gen-data-type value)
                           ""))]}))

(defn- field-value-validation
  "Validates the given additional attribute field value matches the data type"
  [data-type field value]
  (try
    (psa/parse-value data-type value)
    nil
    (catch Exception _
      [(format "%s [%s] is not a valid value for type [%s]."
               (v/humanize-field field) value (psa/gen-data-type data-type))])))

(defn- values-match-data-type-validation
  "Validates additional attribute values match the data type"
  [field-path aa]
  (let [{:keys [data-type]} aa
        errors (->> (select-keys aa [:parameter-range-begin :parameter-range-end :value])
                    (mapcat #(apply field-value-validation data-type %))
                    (remove nil?))]
    (when (seq errors)
      {field-path errors})))

(def psa-validations
  "Defines the product specific attribute validations for collections"
  [{:data-type data-type-validator}
   values-match-data-type-validation])

