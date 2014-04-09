(ns cmr.search.data.temporal-to-elastic
  "Contains functions to map from a temporal condition to elastic search query"
  (:require [cmr.search.data.query-to-elastic :as q]
            [cmr.search.models.query :as qm]))

;; TODO the name of the function is mis-leading as we are only dealing with temporal with range date time here
;; when we add more temporal support in the future, this name will start making sense. Not bothering with the right name for now.
(defn- temporal->simple-conditions
  "Convert a temporal condition into a combination of simpler conditions so that it will be easier to convert into elastic json"
  [temporal]
  (let [{:keys [start-date end-date]} (:date-range-condition temporal)
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

;; Convert a temporal condition to elastic json
(extend-protocol q/ConditionToElastic
  cmr.search.models.query.TemporalCondition
  (condition->elastic
    [temporal]
    (q/condition->elastic
      (temporal->simple-conditions temporal))))