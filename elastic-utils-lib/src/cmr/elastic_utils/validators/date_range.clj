(ns cmr.elastic-utils.validators.date-range
  "Contains functions for validating date range condition"
  (:require [clj-time.core :as t]
            [cmr.elastic-utils.es-query-validation :as v]
            [cmr.elastic-utils.datetime-helper :as h])
  (:import cmr.common.services.search.query_model.DateRangeCondition))

(defn- start-date-is-before-end-date
  "Validates start-date is before end-date"
  [date-range]
  (let [{:keys [start-date end-date]} date-range]
    (if (and start-date end-date (t/after? start-date end-date))
      [(format "start_date [%s] must be before end_date [%s]"
               (h/datetime->string start-date) (h/datetime->string end-date))]
      [])))

(extend-protocol v/Validator
  cmr.common.services.search.query_model.DateRangeCondition
  (validate
    [date-range]
    (start-date-is-before-end-date date-range)))
