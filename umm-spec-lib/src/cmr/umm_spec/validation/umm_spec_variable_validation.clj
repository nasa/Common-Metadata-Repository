(ns cmr.umm-spec.validation.umm-spec-variable-validation
  "Defines validations for UMM variables."
  (:require
   [cmr.common.validations.core :as v]
   [cmr.umm.validation.validation-utils :as vu]))

(def variable-validations
  "Defines validations for variables."
  {})

(def variable-validation-warnings
  "Defines validations for variables that we want to return as warnings and not
  as failures."
  {})
