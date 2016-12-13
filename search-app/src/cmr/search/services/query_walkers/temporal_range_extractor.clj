(ns cmr.search.services.query-walkers.temporal-range-extractor
  "Defines protocols and functions to extract keywords from a query. The extraction is done
  during pre-processing of the query to get the temporal conditions result feature."
  (:require
   [clojure.string :as str]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.models.query :as qm]))

(defprotocol ExtractTemporalRanges
  "Defines a function to extract temporal ranges. Temporal ranges are returned in the format
  {:start-date :end-date}. Start date or end date can be nil and missing from the map."
  (extract-temporal-ranges
    [c]
    "Extracts temporal ranges from conditions.")
  (contains-temporal-range-condition?
   [c]
   "Returns true if the query contains a temporal condition"))

(defn extract-query-temporal-ranges
  "Extracts temporal ranges from Temporal conditions"
  [c]
  (extract-temporal-ranges c))

(defn contains-temporal-ranges?
  [c]
  (contains-temporal-range-condition? c))

(extend-protocol ExtractTemporalRanges
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (extract-temporal-ranges
   [query]
   (extract-temporal-ranges (:condition query)))
  (contains-temporal-range-condition?
   [query]
   (contains-temporal-range-condition? (:condition query)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (extract-temporal-ranges
   [{:keys [conditions]}]
   (mapcat extract-temporal-ranges conditions))
  (contains-temporal-range-condition?
   [{:keys [conditions]}]
   (some contains-temporal-range-condition? conditions))

  ; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.TemporalCondition
  (extract-temporal-ranges
   [{:keys [start-date end-date]}]
   [(util/remove-nil-keys
     {:start-date start-date
      :end-date end-date})])
  (contains-temporal-range-condition?
   [_]
   true)

  ; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; ;; catch all extractor
  java.lang.Object
  (extract-temporal-ranges
   [this]
   nil)
  (contains-temporal-range-condition?
   [this]
   false))
