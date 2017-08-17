(ns cmr.search.services.query-execution.temporal-conditions-results-feature
  "Functions for temporal pre-processing. Add concept-type to the condition because
  when we create the elastic conditions we need special processing based on
  concept type. Pull out temporal conditions in pre-processing and get the temporal
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
  (let [query (assoc-in query [:condition :concept-type] (:concept-type query))]
    (if-let [temporal-ranges (temporal-range-extractor/extract-query-temporal-ranges query)]
      (assoc query ::temporal-ranges temporal-ranges)
      query)))

(defn get-query-temporal-conditions
 [query]
 (::temporal-ranges query))

(defn contains-temporal-conditions?
 [query]
 (some? (::temporal-ranges query)))
