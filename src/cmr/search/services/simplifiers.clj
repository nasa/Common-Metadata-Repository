(ns cmr.search.services.simplifiers
  "Defines protocols and functions to simplify complex conditions"
  (:require [cmr.search.models.query :as qm]))

(defprotocol SimplifyCondition
  "Defines a function to simplify a condition into basic conditions
  that can be easily translated into elastic json"
  (simplify
    [c]
    "Converts a query model condition into equivalent simple conditions"))

(defn simplify-query
  "Return the simplified condition in the query"
  [query]
  (simplify (:condition query)))

(extend-protocol SimplifyCondition
  cmr.search.models.query.ConditionGroup
  (simplify
    [{:keys [operation conditions]}]
    (let [simplified-conditions (map simplify conditions)]
      (qm/->ConditionGroup operation simplified-conditions)))

  cmr.search.models.query.StringCondition
  (simplify [this] this)

  cmr.search.models.query.ExistCondition
  (simplify [this] this)

  cmr.search.models.query.MissingCondition
  (simplify [this] this)

  cmr.search.models.query.DateRangeCondition
  (simplify [this] this)

  cmr.search.models.query.TemporalCondition
  (simplify
    [this]
    (let [{:keys [start-date end-date]} (:date-range-condition this)
          conditions (if end-date
                       [(qm/map->DateRangeCondition {:field :start-date
                                                     :end-date end-date})
                        (qm/or-conds [(qm/map->MissingCondition {:field :end-date})
                                      (qm/map->DateRangeCondition {:field :end-date
                                                                   :start-date start-date})])]
                       [(qm/or-conds [(qm/map->MissingCondition {:field :end-date})
                                      (qm/map->DateRangeCondition {:field :end-date
                                                                   :start-date start-date})])])]
      (qm/and-conds (concat
                      [(qm/map->ExistCondition {:field :start-date})]
                      conditions))))

  cmr.search.models.query.MatchAllCondition
  (simplify [this] this))

