(ns cmr.search.validators.validation
  "Defines protocols and functions to validate conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.spatial.validation :as spatial-validation]
            [clojure.set]
            [cmr.common.mime-types :as mt]

            ;; Must be required to be available.
            [cmr.spatial.ring-validations]))

(def concept-type->supported-result-formats
  "Supported search result formats by concept."
  {:collection #{:xml, :json, :echo10, :dif, :dif10, :atom, :iso19115, :kml, :opendata}
   :granule #{:xml, :json, :echo10, :atom, :iso19115, :csv, :kml}})

(defn validate-result-format
  "Validate requested search result format."
  [concept-type result-format]
  (let [mime-type (mt/format->mime-type result-format)]
    (when-not (get (concept-type->supported-result-formats concept-type) result-format)
      [(format "The mime type [%s] is not supported for %ss." mime-type (name concept-type))])))

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
      (if (seq errors) errors (validate condition))))

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
