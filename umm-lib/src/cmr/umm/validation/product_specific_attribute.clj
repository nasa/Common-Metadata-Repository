(ns cmr.umm.validation.product-specific-attribute
  "Defines validations for UMM collection product specific attribute."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]
            [cmr.common.util :as util]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(def no-range-data-types
  "Set of data-types of additional attribute that do not support parameter range"
  #{:string :boolean})

(def validate-range-data-types
  "Set of data-types of additional attribute that require parameter range validations"
  #{:int :float :datetime :date :time})

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

(defn- range-type-validation
  "Validates data type of the given additional attribute supports range if range value is present"
  [aa]
  (let [{:keys [data-type]} aa
        value-fn (fn [field value]
                   (when value
                     [(format "%s is not allowed for type [%s]"
                              (v/humanize-field field) (psa/gen-data-type data-type))]))]
    (when (no-range-data-types data-type)
      (->> (select-keys aa [:parameter-range-begin :parameter-range-end])
           (mapcat #(apply value-fn %))
           (remove nil?)))))

(defn- range-values-validation
  "Validates range values"
  [aa]
  (let [{:keys [data-type parsed-parameter-range-begin parsed-parameter-range-end parsed-value]} aa]
    (when (validate-range-data-types data-type)
      (cond
        (and parsed-parameter-range-begin parsed-parameter-range-end
             (util/greater-than? parsed-parameter-range-begin parsed-parameter-range-end))
        [(format "Parameter Range Begin [%s] cannot be greater than Parameter Range End [%s]."
                 (psa/gen-value data-type parsed-parameter-range-begin)
                 (psa/gen-value data-type parsed-parameter-range-end))]

        (and parsed-value parsed-parameter-range-begin
             (util/less-than? parsed-value parsed-parameter-range-begin))
        [(format "Value [%s] cannot be less than Parameter Range Begin [%s]."
                 (psa/gen-value data-type parsed-value)
                 (psa/gen-value data-type parsed-parameter-range-begin))]

        (and parsed-value parsed-parameter-range-end
             (util/greater-than? parsed-value parsed-parameter-range-end))
        [(format "Value [%s] cannot be greater than Parameter Range End [%s]."
                 (psa/gen-value data-type parsed-value)
                 (psa/gen-value data-type parsed-parameter-range-end))]))))

(defn- parameter-range-validation
  "Validates additional attribute parameter range related rules"
  [field-path aa]
  (let [errors (concat
                 (range-type-validation aa)
                 (range-values-validation aa))]
    (when (seq errors)
      {field-path errors})))

(def psa-validations
  "Defines the product specific attribute validations for collections"
  [{:data-type data-type-validator}
   values-match-data-type-validation
   parameter-range-validation])

(defn- value-refs-match-data-type-validation
  "Validates granule additional attribute ref values match the parent data type"
  [field-path aa]
  (let [data-type (get-in aa [:parent :data-type])
        errors (->> (:values aa)
                    (mapcat (partial field-value-validation data-type :value))
                    (remove nil?))]
    (when (seq errors)
      {field-path errors})))

(defn- value-refs-parameter-range-validation
  "Validates granule satisfy parent collection's additional attribute parameter range rules"
  [field-path aa]
  (let [{:keys [name values parent]} aa
        {:keys [data-type]} parent
        errors (if (seq values)
                 (when parent
                   (->> (map (partial psa/safe-parse-value data-type) values)
                        (mapcat (comp range-values-validation (partial assoc parent :parsed-value)))
                        (remove nil?)))
                 [(format "%%s [%s] values must not be empty." name)])]
    (when (seq errors)
      {field-path errors})))

(def psa-ref-validations
  "Defines the product specific attribute reference validations for granules"
  [value-refs-match-data-type-validation
   value-refs-parameter-range-validation])
