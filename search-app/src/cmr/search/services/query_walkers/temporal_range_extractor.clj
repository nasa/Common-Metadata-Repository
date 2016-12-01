(ns cmr.search.services.query-walkers.temporal-range-extractor
  "Defines protocols and functions to extract keywords from a query."
  (:require
   [clojure.string :as str]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.models.query :as qm]))

(defprotocol ExtractTemporalRanges
  "Defines a function to extract temporal ranges"
  (extract-temporal-ranges-seq
    [c]
    "Extracts temporal ranges from conditions.")
  (contains-temporal-range-condition?
   [c]
   "Returns true if the query contains a date range condition"))

(defn extract-temporal-ranges
  "Extracts temporal ranges from Date Range conditions"
  [c]
  (extract-temporal-ranges-seq c))

(defn contains-temporal-ranges?
  [c]
  (contains-temporal-range-condition? c))

(extend-protocol ExtractTemporalRanges
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (extract-temporal-ranges-seq
   [query]
   (when-let [ranges (extract-temporal-ranges-seq (:condition query))]
     (flatten ranges)))
  (contains-temporal-range-condition?
   [query]
   (contains-temporal-range-condition? (:condition query)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (extract-temporal-ranges-seq
   [{:keys [conditions]}]
   (when-let [ranges (seq (remove nil? (map extract-temporal-ranges-seq conditions)))]
     ;; If a DateRangeCondition exists at the top level of the condition group, merge
     ;; the conditions together to get both start and end date, if applicable
     (if (some #{cmr.common_app.services.search.query_model.DateRangeCondition}
               (map type conditions))
       (apply merge ranges)
       ranges)))
  (contains-temporal-range-condition?
   [{:keys [conditions]}]
   (reduce (fn [_ condition]
             (when (contains-temporal-range-condition? condition)
               (reduced true)))
           false
           conditions))

  ; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.DateRangeCondition
  (extract-temporal-ranges-seq
   [{:keys [start-date end-date]}]
   (util/remove-nil-keys
    {:start-date start-date
     :end-date end-date}))
  (contains-temporal-range-condition?
   [_]
   true)

  ; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; ;; catch all extractor
  java.lang.Object
  (extract-temporal-ranges-seq
   [this]
   nil)
  (contains-temporal-range-condition?
   [this]
   false))
