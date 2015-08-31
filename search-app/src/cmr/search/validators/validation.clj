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
  {:collection #{:xml, :json, :umm-json, :echo10, :dif, :dif10, :atom, :iso19115, :kml,
                 :opendata, :native}
   :granule #{:xml, :json, :echo10, :atom, :iso19115, :csv, :kml, :native}})

(def all-revisions-supported-result-formats
  "Supported search result format when all-revisions? is true."
  #{:umm-json :xml})

(defn validate-result-format-for-all-revisions
  "Validate requested search result format for all-revisions?."
  [all-revisions? result-format]
  (when all-revisions?
    (let [mime-type (mt/format->mime-type result-format)]
      (when-not (contains? all-revisions-supported-result-formats result-format)
        [(format "The mime type [%s] is not supported when all_revisions = true." mime-type)]))))

(defn validate-concept-type-result-format
  "Validate requested search result format for concept type."
  [concept-type result-format]
  (let [mime-type (mt/format->mime-type result-format)]
    (when-not (get (concept-type->supported-result-formats concept-type) result-format)
      [(format "The mime type [%s] is not supported for %ss." mime-type (name concept-type))])))

(defn validate-highlights-format
  "Validates that the include_highlights parameter is only set to true when the result format is
  JSON"
  [result-features result-format]
  (when (and (some #{:highlights} result-features)
             (not= :json result-format))
    ["Highlights are only supported in the JSON format."]))

(defprotocol Validator
  "Defines the protocol for validating query conditions.
  A sequence of errors should be returned if validation fails, otherwise an empty sequence is returned."
  (validate
    [c]
    "Validate condition and return errors if found"))

(extend-protocol Validator
  cmr.search.models.query.Query
  (validate
    [{:keys [concept-type result-format all-revisions? condition result-features]}]
    (let [errors (concat (validate-concept-type-result-format concept-type result-format)
                         (validate-result-format-for-all-revisions all-revisions? result-format)
                         (validate-highlights-format result-features result-format))]
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
