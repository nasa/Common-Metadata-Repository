(ns cmr.search.validators.validation
  "Defines protocols and functions to validate conditions"
  (:require
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common-app.services.search.query-validation :as cqv]
   [cmr.common.mime-types :as mt]
   [cmr.search.models.query :as qm]
   [cmr.search.validators.all-granule-validation :as all-granule-validation]
   [cmr.search.validators.leading-wildcard-validation :as lwv]
   [cmr.spatial.validation :as spatial-validation]
   [cmr.umm-spec.versioning :as umm-version])
  ;; Must be required to be available.
  (:require
   cmr.spatial.ring-validations))

(defn- umm-versioned-result-formats
  "Returns the umm versioned result formats for the given concept-type"
  [concept-type]
  (for [format-key [:umm-json :umm-json-results]
        version (umm-version/versions concept-type)]
    {:format format-key
     :version version}))

(defmethod cqv/supported-result-formats :collection
  [_]
  (into #{:xml :json :legacy-umm-json :echo10 :dif :dif10 :atom :iso19115 :kml :opendata :native
          ;; umm-json supported with and without versions
          :umm-json :umm-json-results}
        (umm-versioned-result-formats :collection)))

(defmethod cqv/supported-result-formats :granule
  [_]
  #{:xml, :json, :echo10, :atom, :iso19115, :csv, :kml, :native :timeline})

(defmethod cqv/supported-result-formats :variable
  [_]
  (into #{:json
          ;; umm-json supported with and without versions
          :umm-json :umm-json-results}
        (umm-versioned-result-formats :variable)))

(def all-revisions-supported-result-formats
  "Supported search result format when all-revisions? is true."
  (into #{:legacy-umm-json :xml :umm-json :umm-json-results}
        (umm-versioned-result-formats :collection)))

(defn validate-result-format-for-all-revisions
  "Validate requested search result format for all-revisions?."
  [{:keys [all-revisions? result-format]}]
  (when all-revisions?
    (let [result-format (cqm/base-result-format result-format)
          mime-type (mt/format->mime-type result-format)]
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

(defmethod cqv/query-validations :granule
  [_]
  [lwv/limit-number-of-leading-wildcard-patterns
   all-granule-validation/no-all-granules-with-spatial
   all-granule-validation/all-granules-exceeds-page-depth-limit
   all-granule-validation/no-all-granules-with-scroll])

(extend-protocol cqv/Validator
  cmr.search.models.query.SpatialCondition
  (validate
    [{:keys [shape]}]
    (spatial-validation/validate shape))

  ;; catch all validator
  java.lang.Object
  (validate [this] []))
