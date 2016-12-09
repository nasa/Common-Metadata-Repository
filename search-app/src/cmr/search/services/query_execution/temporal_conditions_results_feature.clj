(ns cmr.search.services.query-execution.temporal-conditions-results-feature
  "Functions to pull out temporal conditions in pre-processing and get the temporal
  range data from the query"
  (:require
   [clojure.string :as str]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common-app.services.search.results-model :as r]
   [cmr.common.util :as util]
   [cmr.search.services.query-walkers.temporal-range-extractor :as temporal-range-extractor]))

(defmethod query-execution/pre-process-query-result-feature :temporal-conditions
  [_ query _]
  (if-let [temporal-ranges (temporal-range-extractor/extract-temporal-ranges query)]
    query
    query))
