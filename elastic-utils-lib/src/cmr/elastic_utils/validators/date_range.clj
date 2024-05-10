(ns cmr.elastic-utils.validators.date-range
  "Contains functions for validating date range condition"
  (:require
   [clj-time.core :as time-core]
   [cmr.elastic-utils.datetime-helper :as date-helper]
   [cmr.elastic-utils.search.es-query-validation :as q-val])
  #_{:clj-kondo/ignore [:unused-import]}
  (:import cmr.common.services.search.query_model.DateRangeCondition))

(defn- start-date-is-before-end-date
  "Validates start-date is before end-date"
  [date-range]
  (let [{:keys [start-date end-date]} date-range]
    (if (and start-date end-date (time-core/after? start-date end-date))
      [(format "start_date [%s] must be before end_date [%s]"
               (date-helper/datetime->string start-date)
               (date-helper/datetime->string end-date))]
      [])))

(extend-protocol q-val/Validator
  cmr.common.services.search.query_model.DateRangeCondition
  (validate
    [date-range]
    (start-date-is-before-end-date date-range)))
