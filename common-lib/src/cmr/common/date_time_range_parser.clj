(ns cmr.common.date-time-range-parser
  "Contains helper functions to parse datetime range string to clj-time interval"
  (:require [cmr.common.services.messages :as msg]
            [clj-time.core :as t]
            [cmr.common.date-time-parser :as dtp]))

(defn- parse-iso-datetime-range
  "Parse an ISO 8601 time interval"
  [iso-value]
  (try
    (let [interval (org.joda.time.Interval/parse iso-value)]
      {:start-date (.getStart interval)
       :end-date (.getEnd interval)})
    (catch java.lang.IllegalArgumentException e
      (msg/data-error :invalid-data msg/invalid-msg :date-range iso-value (.getMessage e)))))


(defn parse-datetime-range
  "Parses a date range parameter in the form of 'value' or 'start-date,end-date' or an ISO 8601
  time interval string (see http://en.wikipedia.org/wiki/ISO_8601#Time_intervals).
  Returns a map of the form {:date date, :start-date start-date, :end-date end-date}
  where start-date and end-date are dates parsed from the input string."
  [^java.lang.String value]
  (if (re-matches #"^.*(,|/).*$" value)
    (let [[_ start seperator stop] (re-find #"^(.*)(,|/)(.*)$" value)]
      (cond
        (empty? start) {:end-date (dtp/parse-datetime stop)}
        (empty? stop) {:start-date (dtp/parse-datetime start)}
        (= seperator ",") {:start-date (dtp/parse-datetime start)
                           :end-date (dtp/parse-datetime stop)}
        (= seperator "/" ) (parse-iso-datetime-range value)))
    {:date (dtp/parse-datetime value)}))