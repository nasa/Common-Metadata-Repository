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
  (:require [cmr.search.data.search-index :as idx]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]))

(defn- validate-query
  "Validates a query model. Throws an exception to return to user with errors.
  Returns the query model if validation is successful so it can be chained with other calls."
  [system query]
  ;; TODO validate query
  query)

(defn- apply-acls
  "Modifies the query to apply ACLs for the current user."
  [system query]
  ;; TODO adjust this operation to take a token or similar and apply ACLS to the query
  query)

(defn- simplify-query
  "Simplifies the query."
  [system query]
  ;; TODO query simplification
  query)

(defn- execute-query
  "Executes a query returning results as concept id, native provider id, and revision id."
  [system query]
  (idx/execute-query (:search-index system) query))

(defn find-concepts-by-query
  "Executes a search for concepts using a query The concepts will be returned with
  concept id and native provider id."
  [system query]
  (->> query
       (validate-query system)
       (apply-acls system)
       (simplify-query system)
       (execute-query system)))

(defn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id."
  [system concept-type params]

  (->> params
       p/validate-parameters
       (p/parameters->query concept-type)
       (find-concepts-by-query system)))
