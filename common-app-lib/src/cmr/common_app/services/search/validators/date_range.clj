(ns cmr.common-app.services.search.validators.date-range
  "Contains functions for validating date range condition"
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.common-app.services.search.query-validation :as v]
            [cmr.common-app.services.search.datetime-helper :as h]))

(defn- start-date-is-before-end-date
  "Validates start-date is before end-date"
  [date-range]
  (let [{:keys [start-date end-date]} date-range]
    (if (and start-date end-date (t/after? start-date end-date))
      [(format "start_date [%s] must be before end_date [%s]"
               (h/datetime->string start-date) (h/datetime->string end-date))]
      [])))

(extend-protocol v/Validator
  cmr.common_app.services.search.query_model.DateRangeCondition
  (validate
    [date-range]
    (start-date-is-before-end-date date-range)))
