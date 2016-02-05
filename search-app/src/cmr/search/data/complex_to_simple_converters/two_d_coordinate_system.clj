(ns cmr.search.data.complex-to-simple-converters.two-d-coordinate-system
  "Defines functions that implement the reduce-query-condition method of the ComplexQueryToSimple
  protocol for two d coordinate system related search fields."
  (:require [cmr.common-app.services.search.query-model :as qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.complex-to-simple :as c2s]
            [cmr.common.services.errors :as errors]))

(defprotocol ConvertCoordinateCondition
  (coordinate-cond->condition
    [coordinate-cond start-key end-key]
    "Converts coordinate condition for the given coordinate index to query condition."))

(extend-protocol ConvertCoordinateCondition
  cmr.search.models.query.CoordinateValueCondition
  (coordinate-cond->condition
    [coordinate-cond start-key end-key]
    (let [value (:value coordinate-cond)
          start-value-cond (qm/numeric-value-condition start-key value)
          end-value-cond (qm/numeric-value-condition end-key value)
          start-exist-condition (qm/->ExistCondition start-key)
          end-exist-condition (qm/->ExistCondition end-key)
          start-range-cond (qm/numeric-range-condition start-key nil value)
          end-range-cond (qm/numeric-range-condition end-key value nil)
          and-clause (gc/and-conds
                       [start-exist-condition end-exist-condition start-range-cond end-range-cond])]
      (gc/or-conds [start-value-cond end-value-cond and-clause])))

  cmr.search.models.query.CoordinateRangeCondition
  (coordinate-cond->condition
    [coordinate-cond start-key end-key]
    (let [{:keys [min-value max-value]} coordinate-cond
          ;; granules with coordinate range contains search coordinate range should be found
          contains-cond
          (when (and min-value max-value)
            (let [start-exist-condition (qm/->ExistCondition start-key)
                  end-exist-condition (qm/->ExistCondition end-key)
                  start-max-cond (qm/numeric-range-condition start-key nil min-value)
                  end-min-cond (qm/numeric-range-condition end-key max-value nil)]
              (gc/and-conds
                [start-exist-condition end-exist-condition start-max-cond end-min-cond])))
          start-range-cond (qm/numeric-range-condition start-key min-value max-value)
          end-range-cond (qm/numeric-range-condition end-key min-value max-value)]
      (gc/or-conds (if contains-cond
                     [contains-cond start-range-cond end-range-cond]
                     [start-range-cond end-range-cond]))))

  nil
  (coordinate-cond->condition
    [coordinate-cond start-key end-key]
    qm/match-all))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.TwoDCoordinateCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [{:keys [coordinate-1-cond coordinate-2-cond]} condition]
      (gc/and-conds
        [(coordinate-cond->condition coordinate-1-cond :start-coordinate-1 :end-coordinate-1)
         (coordinate-cond->condition coordinate-2-cond :start-coordinate-2 :end-coordinate-2)]))))

(defn- two-d-conditions->condition
  "Returns the query condition for the given two d coordinates search params."
  [two-d-conditions context]
  (if two-d-conditions
    (gc/or-conds (map #(c2s/reduce-query-condition % context) two-d-conditions))
    qm/match-all))


(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.TwoDCoordinateSystemCondition
  (c2s/reduce-query-condition
    [condition context]
    (let [{:keys [two-d-name two-d-conditions case-sensitive?]} condition
          two-d-name-cond (qm/string-condition :two-d-coord-name two-d-name case-sensitive? false)
          coord-cond (two-d-conditions->condition two-d-conditions context)]
      (gc/and-conds [two-d-name-cond coord-cond]))))

