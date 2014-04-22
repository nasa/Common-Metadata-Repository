(ns cmr.search.data.temporal-to-elastic
  "Contains functions to map from a temporal condition to elastic search query"
  (:require [clj-time.core :as t]
            [cmr.search.data.query-to-elastic :as q]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameter-converters.temporal :as temporal]))

(defn- intersect-temporal->simple-conditions
  "Convert a temporal condition with INTERSECT mask into a combination of simpler conditions so that it will be easier to convert into elastic json"
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

(defn- periodic-temporal->simple-conditions
  "Convert a periodic temporal condition into a combination of simpler conditions so that it will be easier to convert into elastic json"
  [temporal]
  (let [{{:keys [start-date end-date]} :date-range-condition
         :keys [start-day end-day]} temporal
        end-year (if (nil? end-date) (t/year (t/now)) (t/year end-date))
        start-day (if (nil? start-day) 1 start-day)
        conditions (map
                     (fn [current-year]
                       (let [current-start (t/plus (t/date-time current-year) (t/days (dec start-day)))
                             current-start (if (t/before? current-start start-date) start-date current-start)
                             current-end (cond
                                           (nil? end-day)
                                           (if (and (= current-year end-year) end-date)
                                             end-date
                                             (t/minus (t/date-time (inc current-year)) (t/seconds 1)))

                                           ;; recurring range does not cross over a year boundary - for example January 25 - May 6
                                           (> end-day start-day)
                                           (let [current-end (t/plus (t/date-time current-year) (t/days (dec end-day)))]
                                             (if (and end-date (t/after? current-end end-date))
                                               end-date
                                               current-end))

                                           ;; recurring range does cross over a year boundary - for example October 1 - March 10
                                           :else
                                           (let [current-end (t/plus (t/date-time (inc current-year)) (t/days (dec end-day)))]
                                             (if (and end-date (t/after? current-end end-date))
                                               end-date
                                               current-end)))]
                         (when-not (t/before? current-end current-start)
                           (intersect-temporal->simple-conditions (temporal/map->temporal-condition {:field :temporal
                                                                                            :start-date current-start
                                                                                            :end-date current-end})))
                         ))
                     (range (t/year start-date) (inc end-year)))]
    (qm/or-conds (remove nil? conditions))))

(defn- temporal->simple-conditions
  "Convert a temporal condition into a combination of simpler conditions so that it will be easier to convert into elastic json"
  [temporal]
  (let [{:keys [start-day end-day]} temporal]
    (if (or (not (nil? start-day)) (not (nil? end-day)))
      (periodic-temporal->simple-conditions temporal)
      (intersect-temporal->simple-conditions temporal))))

;; Convert a temporal condition to elastic json
(extend-protocol q/ConditionToElastic
  cmr.search.models.query.TemporalCondition
  (condition->elastic
    [temporal]
    (q/condition->elastic
      (temporal->simple-conditions temporal))))
