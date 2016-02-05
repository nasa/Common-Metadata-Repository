(ns cmr.search.services.query-walkers.condition-extractor
  "Defines protocols and functions to extract conditions."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.search.models.query :as qm])
  (:import [cmr.common_app.services.search.query_model
            Query
            ConditionGroup
            NegatedCondition
            NestedCondition]
           cmr.search.models.query.CollectionQueryCondition))

(defprotocol ExtractCondition
  (extract-condition-impl
    [c condition-path extract-tester]
    "Extract conditions from the query object. Returns a sequence of conditions that pass the
    extract-tester function. condition-path containins the parent conditions to the condition being
    tested. extract-tester should take two arguments: the condition-path and the condition."))

(defn extract-conditions
  "Extract conditions from the query object. Returns a sequence of conditions that pass the
    extract-tester function."
  [query extract-tester]
  (extract-condition-impl query [] extract-tester))

(defn extract-self
  "A helper for handling a condition. Tests the condition to see if it matches. Returns a single
  item vector with the condition if it does."
  [condition-path extract-tester c]
  (when (extract-tester condition-path c)
    [c]))

(extend-protocol ExtractCondition
  Query
  (extract-condition-impl
    [query _ extract-tester]
    (extract-condition-impl (:condition query)
                       [query]
                       extract-tester))

  ConditionGroup
  (extract-condition-impl
    [group condition-path extract-tester]
    (concat (extract-self condition-path extract-tester group)
            (let [condition-path (conj condition-path group)]
              (mapcat #(extract-condition-impl % condition-path extract-tester)
                      (:conditions group)))))

  CollectionQueryCondition
  (extract-condition-impl
    [coll-query-cond condition-path extract-tester]
    (concat (extract-self condition-path extract-tester coll-query-cond)
            (let [condition-path (conj condition-path coll-query-cond)]
              (extract-condition-impl (:condition coll-query-cond) condition-path extract-tester))))

  NegatedCondition
  (extract-condition-impl
    [negated-cond condition-path extract-tester]
    (concat (extract-self condition-path extract-tester negated-cond)
            (let [condition-path (conj condition-path negated-cond)]
              (extract-condition-impl (:condition negated-cond) condition-path extract-tester))))

  NestedCondition
  (extract-condition-impl
    [c condition-path extract-tester]
    (concat (extract-self condition-path extract-tester c)
            (let [condition-path (conj condition-path c)]
              (extract-condition-impl (:condition c) condition-path extract-tester))))

  ;; catch all extractor
  java.lang.Object
  (extract-condition-impl
    [this condition-path extract-tester]
    (extract-self condition-path extract-tester this)))

