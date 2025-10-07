(ns cmr.elastic-utils.search.es-group-query-conditions
  "Contains conditions for group together multiple conditions. These are the boolean operations
  for AND and OR."
  (:require
   [cmr.common.services.errors :as errors]
   [cmr.common.services.search.query-model :as q]
   [cmr.elastic-utils.search.es-condition-merger :as condition-merger])
  (:refer-clojure :exclude [and or])
  (:import cmr.common.services.search.query_model.ConditionGroup))

(defn- flatten-group-conds
  "Looks for group conditions of the same operation type in the conditions to create. If they are
  of the same operation type (i.e. AND condition groups within an AND) then it will return the child
  conditions of those condition groups"
  [operation conditions]
  (mapcat (fn [c]
            (if (clojure.core/and (= (type c) ConditionGroup)
                                  (= operation (:operation c)))
              (:conditions c)
              [c]))
          conditions))

(defmulti filter-group-conds
  "Filters out conditions from group conditions that would have no effect on the query."
  (fn [operation _conditions]
    operation))

(defmethod filter-group-conds :and
  [_operation conditions]
  ;; Match all conditions can be filtered out of an AND.
  (if (= conditions [q/match-all])
    ;; A single match-all should be returned
    conditions
    (filter #(not= % q/match-all) conditions)))

(defmethod filter-group-conds :or
  [_operation conditions]
  ;; Match none conditions can be filtered out of an OR.
  (if (= conditions [q/match-none])
    ;; A single match-none should be returned
    conditions
    (filter #(not= % q/match-none) conditions)))

(defmulti short-circuit-group-conds
  "Looks for conditions that will overrule all other conditions such as a match-none or match-all.
  If the condition group contains one of those conditions it will return just that condition."
  (fn [operation _conditions]
    operation))

(defmethod short-circuit-group-conds :and
  [_operation conditions]
  (if (some #(= q/match-none %) conditions)
    [q/match-none]
    conditions))

(defmethod short-circuit-group-conds :or
  [_operation conditions]
  (if (some #(= q/match-all %) conditions)
    [q/match-all]
    conditions))

(defn group-conds
  "Combines the conditions together in the specified type of group."
  [operation conditions]
  (when (empty? conditions) (errors/internal-error! "Grouping empty list of conditions"))

  (let [processed-conditions (->> conditions
                        (flatten-group-conds operation)
                        (condition-merger/merge-conditions operation)
                        (short-circuit-group-conds operation)
                        (filter-group-conds operation))]

    (when (empty? processed-conditions)
      (errors/internal-error! (format "Logic error while grouping initial conditions [%s] with operation [%s]. No conditions found"
                                      conditions, operation)))

    (if (= (count processed-conditions) 1)
      (first processed-conditions)
      (q/->ConditionGroup operation processed-conditions))))

(defn and-conds
  "Returns a condition representing conditions combined using a logical AND."
  [conditions]
  (group-conds :and conditions))

(defn or-conds
  "Returns a condition representing conditions combined using a logical OR."
  [conditions]
  (group-conds :or conditions))

(defn and
  "Like and-conds but variadic."
  [& conds]
  (and-conds conds))

(defn or
  "Like or-conds but variadic."
  [& conds]
  (or-conds conds))
