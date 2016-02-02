(ns cmr.common-app.services.search
  "This contains common code for implementing search capabilities in a CMR application"
  (:require [cmr.common.util :as u]
            [cmr.common-app.services.search.query-validation :as qv]
            [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.common.log :refer (debug info warn error)]))

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
    [(:concept-type query) (:result-format query)]))

(defmulti single-result->response
  "Returns a string representation of a single concept in the format
  specified in the query."
  (fn [context query results]
    [(:concept-type query) (:result-format query)]))



(defn find-concepts
  "Executes a search for concepts using the given query."
  [context concept-type params query]
  (validate-query context query)
  (let [[query-execution-time results] (u/time-execution (qe/execute-query context query))
        [result-gen-time result-str] (u/time-execution
                                      (search-results->response
                                       context query (assoc results :took query-execution-time)))
        total-took (+ query-execution-time result-gen-time)]
    (debug "query-execution-time:" query-execution-time "result-gen-time:" result-gen-time)

    (info (format "Found %d %ss in %d ms in format %s with params %s."
                  (:hits results) (name concept-type) (:total-took results) (:result-format query)
                  (pr-str params)))

    {:results result-str :hits (:hits results) :took query-execution-time :total-took total-took
     :result-format (:result-format query)}))
