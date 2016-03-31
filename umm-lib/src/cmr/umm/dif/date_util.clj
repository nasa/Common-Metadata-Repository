(ns cmr.umm.dif.date-util
  (:require [clj-time.format :as f]
            [clj-time.core :as t]
            [cmr.common.date-time-parser :as dtp]))

(defn end-of-day
  "Returns a DateTime value representing the last millisecond of the day of given date.

  Example:
    (str (end-of-day (t/date-time 2015 4 1))) => \"2015-04-01T23:59:59.999Z\""
  [date]
  (-> (t/date-time (t/year date) (t/month date) (t/day date))
      (t/plus (t/days 1))
      (t/minus (t/millis 1))))

(defn parse-dif-end-date
  "Returns a date parsed according to DIF end date logic."
  [s]
  (when s
    (if (re-find #"^\d\d\d\d-\d\d-\d\d$" s)
      (end-of-day (f/parse (:date f/formatters) s))
      (dtp/parse-datetime s))))
