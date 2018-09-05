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

(defprotocol AddConceptTypeToTemporalCondition
  "Defines a function to add concept type to the temporal conditions within the query."
  (add-concept-type-to-temporal-condition
    [c concept-type]
    "Add concept type to temporal condition."))

(extend-protocol AddConceptTypeToTemporalCondition
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (add-concept-type-to-temporal-condition
   [query concept-type]
   (update query :condition add-concept-type-to-temporal-condition concept-type))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (add-concept-type-to-temporal-condition
   [cg concept-type]
   (util/update-in-each cg [:conditions] add-concept-type-to-temporal-condition concept-type))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.NestedCondition
  (add-concept-type-to-temporal-condition
   [nc concept-type]
   (update nc :condition add-concept-type-to-temporal-condition concept-type))

  ; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.TemporalCondition
  (add-concept-type-to-temporal-condition
   [temporal concept-type]
   (assoc temporal :concept-type concept-type))

  ; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; ;; catch all extractor
  java.lang.Object
  (add-concept-type-to-temporal-condition
   [this _]
   this))

(defmethod query-execution/pre-process-query-result-feature :temporal-conditions
  [_ query _]
  (let [query (add-concept-type-to-temporal-condition query (:concept-type query))]
    (if-let [temporal-ranges (temporal-range-extractor/extract-query-temporal-ranges query)]
      (assoc query ::temporal-ranges temporal-ranges)
      query)))

(defn get-query-temporal-conditions
 [query]
 (::temporal-ranges query))

(defn contains-temporal-conditions?
 [query]
 (some? (::temporal-ranges query)))
