(ns cmr.search.services.query-execution.temporal-conditions-results-feature
  "Functions to pull out temporal conditions in pre-processing and get the temporal
  range data from the query. This is done during pre-processing since down the road temporal
  conditions get very complicated and it's easier to pull them out here."
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
    (assoc query ::temporal-ranges temporal-ranges)
    query))

(defn get-query-temporal-conditions
 [query]
 (::temporal-ranges query))

(defn contains-temporal-conditions?
 [query]
 (some? (::temporal-ranges query)))
