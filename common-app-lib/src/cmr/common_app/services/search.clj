(ns cmr.common-app.services.search
  "This contains common code for implementing search capabilities in a CMR application"
  (:require [cmr.common.util :as u]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-validation :as qv]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.common-app.services.search.query-model :as qm]
            ;; Must be required to be available
            [cmr.common-app.services.search.validators.numeric-range]
            [cmr.common-app.services.search.validators.date-range]))


(defn validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [context query]
  (if-let [errors (seq (qv/validate query))]
    (errors/throw-service-errors :bad-request errors)
    query))

(defmulti search-results->response
  "Converts query search results into a string response."
  (fn [context query results]
    [(:concept-type query) (qm/base-result-format query)]))

(defmulti single-result->response
  "Returns a string representation of a single concept in the format
  specified in the query."
  (fn [context query results]
    [(:concept-type query) (qm/base-result-format query)]))

(defn find-concepts
  "Executes a search for concepts using the given query."
  [context concept-type query]
  (validate-query context query)
  (let [[query-execution-time results] (u/time-execution (qe/execute-query context query))
        [result-gen-time result-str] (u/time-execution
                                      (search-results->response
                                       context query (assoc results :took query-execution-time)))]
    (debug "query-execution-time:" query-execution-time "result-gen-time:" result-gen-time)

    {:results result-str
     :hits (:hits results)
     :result-format (:result-format query)}))
