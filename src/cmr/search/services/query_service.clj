(ns cmr.search.services.query-service
  "Defines operations for querying for concepts.

  Steps to process a query:

  - Validate parameters
  - Convert parameters into query model
  - Query validation
  - Apply ACLs to query
  - Query simplification
  - Convert Query into Elastic Query Model
  - Send query to Elasticsearch
  - Convert query results into requested format"
  (:require [cmr.search.data.elastic-search-index :as idx]
            [cmr.search.models.query :as qm]

            ;; parameter-converters
            ;; These must be required here to make multimethod implementations available.
            [cmr.search.services.parameters :as p]
            [cmr.search.services.parameter-converters.collection-query]
            [cmr.search.services.parameter-converters.temporal]

            ;; Validation
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.temporal]
            [cmr.search.validators.date-range]

            [cmr.search.services.parameter-validation :as pv]
            [cmr.search.services.collection-query-resolver :as r]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.common.services.errors :as err]))

(deftracefn validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [context query]
  (let [errors (v/validate query)]
    (when-not (empty? errors)
      (err/throw-service-errors :invalid-data errors))
    query))

(deftracefn apply-acls
  "Modifies the query to apply ACLs for the current user."
  [context query]
  ;; TODO adjust this operation to take a token or similar and apply ACLS to the query
  query)

(deftracefn resolve-collection-query
  "Replace the collection query conditions in the query with conditions of collection-concept-ids."
  [context query]
  (r/resolve-collection-query query context))

(deftracefn simplify-query
  "Simplifies the query."
  [context query]
  ;; TODO query simplification
  query)

(deftracefn execute-query
  "Executes a query returning results as concept id, native provider id, and revision id."
  [context page-size query]
  (idx/execute-query context query page-size))

(deftracefn find-concepts-by-query
  "Executes a search for concepts using a query The concepts will be returned with
  concept id and native provider id."
  [context page-size query]
  (->> query
       (validate-query context)
       (apply-acls context)
       (resolve-collection-query context)
       (simplify-query context)
       (execute-query context page-size)))

(deftracefn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id."
  [context concept-type params page-size]

  (->> params
       p/replace-parameter-aliases
       (pv/validate-parameters concept-type)
       (p/parameters->query concept-type)
       (find-concepts-by-query context page-size)))
