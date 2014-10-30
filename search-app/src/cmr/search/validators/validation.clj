(ns cmr.search.validators.validation
  "Defines protocols and functions to validate conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.spatial.validation :as spatial-validation]
<<<<<<< HEAD
            [clojure.set]
            [cmr.common.mime-types :as mt]
=======
            [cmr.search.validators.result-format :as rfmt]
>>>>>>> CMR-599: search result format validation moved to Query validation.

            ;; Must be required to be available.
            [cmr.spatial.geodetic-ring-validations]))

;; Records search result supported formats by concept.
;; TODO - its hard to discover supported search result formats are maintained here.
;; Find a better way.
(def concept-type->supported-result-formats
  {:collection #{:xml, :json, :echo10, :dif, :atom, :iso19115, :csv, :kml}
   :granule #{:xml, :json, :echo10, :atom, :iso19115, :csv, :kml}})

(defn validate-result-format
  "Validate requested search result format."
  [concept-type result-format]
  (let [mime-type (mt/format->mime-type result-format)
        valid-mime-types (set (map #(mt/format->mime-type %)
                                   (concept-type->supported-result-formats concept-type)))]
    (mt/validate-request-mime-type mime-type valid-mime-types)))

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
    (validate-result-format concept-type result-format)
    (validate condition))

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
