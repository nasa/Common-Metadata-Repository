(ns cmr.umm.validation.product-specific-attribute
  "Defines validations for UMM collection product specific attribute."
  (:require [cmr.umm.collection :as c]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(defn- data-type-validator
  "Validates data-type is one of the valid product specific attribute data types"
  [field-path value]
  (when-not (some #{value} c/product-specific-attribute-types)
    {field-path [(format "%%s data-type [%s] is not a valid data type."
                         (if value
                           (psa/gen-data-type value)
                           ""))]}))

(def psa-validations
  "Defines the product specific attribute validations for collections"
  {:data-type data-type-validator})

