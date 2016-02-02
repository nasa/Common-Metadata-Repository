(ns cmr.common-app.services.search.group-query-conditions
  "Contains conditions for group together multiple conditions. These are the boolean operations
  for AND and OR."
  (:require [cmr.common-app.services.search.query-model :as q]
            [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.condition-merger :as condition-merger])
  (:import [cmr.common_app.services.search.query_model
            ConditionGroup]))

(defn- flatten-group-conds
  "Looks for group conditions of the same operation type in the conditions to create. If they are
  of the same operation type (i.e. AND condition groups within an AND) then it will return the child
  conditions of those condition groups"
  [operation conditions]
  (mapcat (fn [c]
            (if (and (= (type c) ConditionGroup)
                     (= operation (:operation c)))
              (:conditions c)
              [c]))
          conditions))

(defmulti filter-group-conds
  "Filters out conditions from group conditions that would have no effect on the query."
  (fn [operation conditions]
    operation))

(defmethod filter-group-conds :and
  [operation conditions]
  ;; Match all conditions can be filtered out of an AND.
  (if (= conditions [q/match-all])
    ;; A single match-all should be returned
    conditions
    (filter #(not= % q/match-all) conditions)))

(defmethod filter-group-conds :or
  [operation conditions]
  ;; Match none conditions can be filtered out of an OR.
  (if (= conditions [q/match-none])
    ;; A single match-none should be returned
    conditions
    (filter #(not= % q/match-none) conditions)))

(defmulti short-circuit-group-conds
  "Looks for conditions that will overrule all other conditions such as a match-none or match-all.
  If the condition group contains one of those conditions it will return just that condition."
  (fn [operation conditions]
    operation))

(defmethod short-circuit-group-conds :and
  [operation conditions]
  (if (some #(= q/match-none %) conditions)
    [q/match-none]
    conditions))

(defmethod short-circuit-group-conds :or
  [operation conditions]
  (if (some #(= q/match-all %) conditions)
    [q/match-all]
    conditions))

(defn group-conds
  "Combines the conditions together in the specified type of group."
  [operation conditions]
  (when (empty? conditions) (errors/internal-error! "Grouping empty list of conditions"))

  (let [conditions (->> conditions
                        (flatten-group-conds operation)
                        (condition-merger/merge-conditions operation)
                        (short-circuit-group-conds operation)
                        (filter-group-conds operation))]

    (when (empty? conditions)
      (errors/internal-error! "Logic error while grouping conditions. No conditions found"))

    (if (= (count conditions) 1)
      (first conditions)
      (q/->ConditionGroup operation conditions))))

(defn and-conds
  "Combines conditions in an AND condition."
  [conditions]
  (group-conds :and conditions))

(defn or-conds
  "Combines conditions in an OR condition."
  [conditions]
  (group-conds :or conditions))