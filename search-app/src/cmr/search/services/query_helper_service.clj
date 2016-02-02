(ns cmr.search.services.query-helper-service
  "Helper queries for other services, for instance fetching collection-level
   data required to construct granule queries or results."
  (:require [cmr.common-app.services.search.query-execution :as qe]
            [cmr.common-app.services.search.query-model :as qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]))

(def ^:private orbit-param-fields
  "The collection fields that describe the orbit as used in orbital back tracking."
  [:concept-id
   :swath-width
   :period
   :inclination-angle
   :number-of-orbits
   :start-circular-latitude])

(defn- scope-by-collection-ids
  "If collection-ids has values, scopes the passed condition to only contain the
   given collections, otherwise does not scope the condition"
  [condition collection-ids]
  (if (seq collection-ids)
    (gc/and-conds [condition (qm/string-conditions :concept-id collection-ids true)])
    condition))

(defn- collections-query
  "Returns an elastic query for the given condition scoped by the given collection
   ids (null or empty seq for all collections) which will return the given fields"
  [condition collection-ids fields]
  (qm/query {:concept-type :collection
             :condition (scope-by-collection-ids condition collection-ids)
             :skip-acls? true
             :page-size :unlimited
             :result-format :query-specified
             :fields fields}))

(defn- fetch-elastic-collections
  "Fetches fields on elastic collections matching the given condition, scoped to
   the given collection-ids (null or empty seq for all collections)."
  [context condition collection-ids fields]
  (let [query (collections-query condition collection-ids fields)]
    (:items (qe/execute-query context query))))

(defn collection-orbit-parameters
  "Fetch elastic orbit parameters for the given collection ids.
   If treat-empty-as-all? is true, scopes the results to the passed collection ids
   and null or empty collection ids implies that no scoping should be applied (all
   collections should be fetched).
   If treat-empty-as-all? is false, treats collection-ids as an additional condition.
   null or empty collection ids implies that no results should be found.
   The scoping rules are kind of ugly, but we need it both ways."
  ([context collection-ids]
   (collection-orbit-parameters context collection-ids false))
  ([context collection-ids treat-empty-as-all?]
   (if (or treat-empty-as-all? (seq collection-ids))
     (fetch-elastic-collections context
                                (qm/->ExistCondition :swath-width)
                                collection-ids
                                orbit-param-fields)
     [])))
