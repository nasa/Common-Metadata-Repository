(ns cmr.spatial.validation
  "Defines common spatial validations"
  (:require [cmr.common.validations.core :as v]))

(defprotocol SpatialValidation
  (validate
    [record]
    "Validates the record and returns a list of error messages. If the list is empty then the record
    is valid."))

(defn spatial-validation
  "Implements a cmr.common.validation function for any spatial area."
  [field-path record]
  (when-let [errors (seq (validate record))]
    {field-path (for [error errors]
                  (str "Spatial validation error: " error))}))
