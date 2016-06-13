(ns cmr.search.validators.validation
  "Defines protocols and functions to validate conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.query-validation :as cqv]
            [cmr.spatial.validation :as spatial-validation]
            [clojure.set]
            [cmr.common.mime-types :as mt]

            ;; Must be required to be available.
            [cmr.spatial.ring-validations]))

(defmethod cqv/supported-result-formats :collection
  [_]
  #{:xml, :json, :umm-json, :echo10, :dif, :dif10, :atom, :iso19115, :kml, :opendata, :native})

(defmethod cqv/supported-result-formats :granule
  [_]
  #{:xml, :json, :echo10, :atom, :iso19115, :csv, :kml, :native})

(def all-revisions-supported-result-formats
  "Supported search result format when all-revisions? is true."
  #{:umm-json :xml})

(defn validate-result-format-for-all-revisions
  "Validate requested search result format for all-revisions?."
  [{:keys [all-revisions? result-format]}]
  (when all-revisions?
    (let [mime-type (mt/format->mime-type result-format)]
      (when-not (contains? all-revisions-supported-result-formats result-format)
        [(format "The mime type [%s] is not supported when all_revisions = true." mime-type)]))))

(defn validate-highlights-format
  "Validates that the include_highlights parameter is only set to true when the result format is
  JSON"
  [{:keys [result-features result-format]}]
  (when (and (some #{:highlights} result-features)
             (not= :json result-format))
    ["Highlights are only supported in the JSON format."]))

(defn validate-facets-v2-format
  "Validates that when request facet-v2 the result format is JSON."
  [{:keys [result-features result-format]}]
  (when (and (some #{:facets-v2} result-features)
             (not= :json result-format))
    ["V2 facets are only supported in the JSON format."]))

(defmethod cqv/query-validations :collection
  [_]
  [validate-result-format-for-all-revisions
   validate-highlights-format
   validate-facets-v2-format])

(extend-protocol cqv/Validator
  cmr.search.models.query.SpatialCondition
  (validate
    [{:keys [shape]}]
    (spatial-validation/validate shape))

  ;; catch all validator
  java.lang.Object
  (validate [this] []))
