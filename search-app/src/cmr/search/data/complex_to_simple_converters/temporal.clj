(ns cmr.search.data.complex-to-simple-converters.temporal
  "Defines functions that implement the reduce-query-condition method of the ComplexQueryToSimple
  protocol for Temporal conditions."

  (:require [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.datetime-helper :as h]
            [cmr.common-app.services.search.complex-to-simple :as c2s]))

(defn- intersect-temporal->simple-conditions
  "Convert a temporal condition with INTERSECT mask into a combination of simpler conditions
  so that it will be easier to convert into elastic json"
  [temporal]
  (let [{:keys [start-date end-date exclusive? limit-to-granules concept-type]} temporal
        [start-date-field end-date-field]
        (if (= :collection concept-type)
          (if limit-to-granules
            [:limit-to-granules-temporals.start-date :limit-to-granules-temporals.end-date]
            [:temporals.start-date :temporals.end-date])
          [:start-date :end-date])
        conditions (if end-date
                     [(cqm/map->DateRangeCondition {:field start-date-field
                                                    :end-date end-date
                                                    :exclusive? exclusive?})
                      (gc/or-conds [(cqm/map->MissingCondition {:field end-date-field})
                                    (cqm/map->DateRangeCondition {:field end-date-field
                                                                  :start-date start-date
                                                                  :exclusive? exclusive?})])]
                     [(gc/or-conds [(cqm/map->MissingCondition {:field end-date-field})
                                    (cqm/map->DateRangeCondition {:field end-date-field
                                                                  :start-date start-date
                                                                  :exclusive? exclusive?})])])
         and-conditions (gc/and-conds (concat
                                        [(cqm/map->ExistCondition {:field start-date-field})]
                                        conditions))]
     (if (= :collection concept-type)
       (if limit-to-granules
         (cqm/nested-condition :limit-to-granules-temporals and-conditions)
         (cqm/nested-condition :temporals and-conditions))
       and-conditions)))

(defn current-end-date
  "Returns the current end datetime for a given year and attributes of a periodic temporal condition"
  [current-year end-date start-day end-day end-year]
  (cond
    (nil? end-day)
    (if (and (= current-year end-year) end-date)
      end-date
      (t/minus (t/date-time (inc current-year)) (t/seconds 1)))

    ;; recurring range does not cross over a year boundary - for example January 25 - May 6
    (>= end-day start-day)
    (let [current-end (t/plus (t/date-time current-year) (t/days (dec end-day)))]
      (if (and end-date (t/after? current-end end-date))
        end-date
        current-end))

    ;; recurring range does cross over a year boundary - for example October 1 - March 10
    :else
    (let [current-end (t/plus (t/date-time (inc current-year)) (t/days (dec end-day)))]
      (if (and end-date (t/after? current-end end-date))
        end-date
        current-end))))

(defn- simple-conditions-for-year
  "Returns simple-conditions constructed for a given year with the periodic temporal condition"
  [current-year start-date end-date start-day end-day end-year limit-to-granules concept-type]
  (let [current-start (t/plus (t/date-time current-year) (t/days (dec start-day)))
        current-start (if (t/before? current-start start-date) start-date current-start)
        current-end (current-end-date current-year end-date start-day end-day end-year)]
    (when-not (t/before? current-end current-start)
      (intersect-temporal->simple-conditions
        (assoc
         (qm/map->TemporalCondition {:start-date current-start
                                     :end-date current-end
                                     :limit-to-granules limit-to-granules})
         :concept-type
         concept-type)))))

(defn- periodic-temporal->simple-conditions
  "Convert a periodic temporal condition into a combination of simpler conditions
  so that it will be easier to convert into elastic json"
  [temporal]
  (let [{:keys [start-day end-day start-date end-date limit-to-granules concept-type]} temporal
        start-date (or start-date h/earliest-start-date-joda-time)]
    (if (or start-date end-date)
      (let [end-year (if end-date (t/year end-date) (t/year (tk/now)))
            start-day (if start-day start-day 1)
            conditions (map
                         #(simple-conditions-for-year
                            % start-date end-date start-day end-day end-year limit-to-granules concept-type)
                         (range (t/year start-date) (inc end-year)))
            conditions (remove nil? conditions)]
        (if (seq conditions)
          (gc/or-conds conditions)
          (errors/throw-service-error
            :bad-request "Periodic temporal search produced no searchable ranges and is invalid.")))
      (errors/throw-service-error
        :bad-request
        "At least temporal_start or temporal_end must be supplied for each temporal condition."))))

(defn- temporal->simple-conditions
  "Convert a temporal condition into a combination of simpler conditions so that it will be easier to convert into elastic json"
  [temporal]
  (let [{:keys [start-day end-day]} temporal]
    (if (or start-day end-day)
      (periodic-temporal->simple-conditions temporal)
      (intersect-temporal->simple-conditions temporal))))

;; Reduce a Temporal condition to simpler conditions
(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.TemporalCondition
  (c2s/reduce-query-condition
    [condition context]
    (temporal->simple-conditions condition)))
