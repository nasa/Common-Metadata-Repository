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
            [cmr.search.services.parameters :as p]
            [cmr.system-trace.core :refer [deftracefn]]))

(deftracefn validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [context query]
  ;; TODO validate query
  query)

(deftracefn apply-acls
  "Modifies the query to apply ACLs for the current user."
  [context query]
  ;; TODO adjust this operation to take a token or similar and apply ACLS to the query
  query)

(deftracefn simplify-query
  "Simplifies the query."
  [context query]
  ;; TODO query simplification
  query)

(deftracefn execute-query
  "Executes a query returning results as concept id, native provider id, and revision id."
  [context query]
  (idx/execute-query context query))

(deftracefn find-concepts-by-query
  "Executes a search for concepts using a query The concepts will be returned with
  concept id and native provider id."
  [context query]
  (->> query
       (validate-query context)
       (apply-acls context)
       (simplify-query context)
       (execute-query context)))

(deftracefn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id."
  [context concept-type params]

  (->> params
       p/replace-parameter-aliases
       p/validate-parameters
       (p/parameters->query concept-type)
       (find-concepts-by-query context)))
