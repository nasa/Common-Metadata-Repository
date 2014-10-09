(ns cmr.spatial.validation
  "Defines a protocol for validating any spatial type")

(defprotocol SpatialValidation
  (validate
    [record]
    "Validates the record and returns a list of error messages. If the list is empty then the record
    is valid. Options will be a map of additional information for validations that is type specific.
    Some validations may require specific options are set."))