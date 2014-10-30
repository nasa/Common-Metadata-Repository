(ns cmr.search.validators.validation
  "Defines protocols and functions to validate conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.spatial.validation :as spatial-validation]
            [clojure.set]
            [cmr.common.mime-types :as mt]

            ;; Must be required to be available.
            [cmr.spatial.geodetic-ring-validations]))

(def concept-type->supported-result-formats
  "Supported search result formats by concept."
  {:collection #{:xml, :json, :echo10, :dif, :atom, :iso19115, :csv, :kml}
   :granule #{:xml, :json, :echo10, :atom, :iso19115, :csv, :kml}})

(defn validate-result-format
  "Validate requested search result format."
  [concept-type result-format]
  (let [mime-type (mt/format->mime-type result-format)
        valid-mime-types (set (map mt/format->mime-type
                                   (concept-type->supported-result-formats concept-type)))]
    (if-not (get valid-mime-types mime-type)
      [(format "The mime type [%s] is not supported for %ss." mime-type (name concept-type))]
      [])))

(defprotocol Validator
  "Defines the protocol for validating query conditions.
  A sequence of errors should be returned if validation fails, otherwise an empty sequence is returned."
  (validate
    [c]
    "Validate condition and return errors if found"))

(extend-protocol Validator
  cmr.search.models.query.Query
  (validate
    [{:keys [concept-type result-format condition]}]
    (let [errors (validate-result-format concept-type result-format)]
      (if-not (empty? errors)
        errors
        (validate condition))))

  cmr.search.models.query.ConditionGroup
  (validate
    [{:keys [conditions]}]
    (mapcat validate conditions))

  cmr.search.models.query.SpatialCondition
  (validate
    [{:keys [shape]}]
    (spatial-validation/validate shape))

  ;; catch all validator
  java.lang.Object
  (validate [this] []))
