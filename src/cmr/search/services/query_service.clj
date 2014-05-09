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
            [cmr.search.services.parameter-converters.attribute]
            [cmr.search.services.parameter-converters.orbit-number]
            [cmr.search.services.parameter-converters.equator-crossing-longitude]

            ;; Validation
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.temporal]
            [cmr.search.validators.date-range]
            [cmr.search.validators.attribute]
            [cmr.search.validators.numeric-range]
            [cmr.search.validators.orbit-number]
            [cmr.search.validators.equator-crossing-longitude]

            [cmr.search.services.parameter-validation :as pv]
            [cmr.search.services.collection-query-resolver :as r]
            [cmr.transmit.metadata-db :as meta-db]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.common.services.errors :as err]
            [cmr.common.util :as u]
            [camel-snake-kebab :as csk]))

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
  [context query]
  (idx/execute-query context query))


(deftracefn find-concepts-by-query
  "Executes a search for concepts using a query The concepts will be returned with
  concept id and native provider id."
  [context query]
  (->> query
       (validate-query context)
       (apply-acls context)
       (resolve-collection-query context)
       (simplify-query context)
       (execute-query context)))

(deftracefn find-concepts-by-parameters
  "Executes a search for concepts using the given parameters. The concepts will be returned with
  concept id and native provider id."
  [context concept-type params]
  (let [params (-> params
                   u/map-keys->kebab-case
                   (update-in [:options] u/map-keys->kebab-case)
                   (update-in [:options] #(when % (into {} (map (fn [[k v]]
                                                                  [k (u/map-keys->kebab-case v)])
                                                                %))))
                   (update-in [:sort-key] #(when % (if (sequential? %)
                                                     (map csk/->kebab-case % )
                                                     (csk/->kebab-case %)))))]
    (->> params
         p/replace-parameter-aliases
         (pv/validate-parameters concept-type)
         (p/parameters->query concept-type)
         (find-concepts-by-query context))))

(deftracefn find-concept-by-id
  "Executes a search to metadata-db and returns the concept with the given cmr-concept-id."
  [context concept-id]
  (meta-db/get-latest-concept context concept-id))
