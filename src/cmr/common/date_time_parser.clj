(ns cmr.common.date-time-parser
  "Contains helper to parse datetime string to clj-time"
  (:require [clj-time.format :as f]))

;; Define the regex for datetime parsing. It will match something like "1986-10-14T04:03:27.456-05:00Z"
(def datetime-regex #"^(\d\d\d\d-(\d)?\d-(\d)?\dT(\d)?\d:(\d)?\d:(\d)?\d(\.\d+)?)(([+-]\d\d:\d\d)|Z)?$")

(defn string->datetime
  "Convert datetime string to Joda DateTime"
  [value]
  (if-let [matches (re-find datetime-regex value)]
    (let [match (get matches 1)
          conformed-value (if (re-find #"\." match) match (str match ".000"))]
      (f/parse (f/formatters :date-hour-minute-second-fraction) conformed-value))
    (throw (Exception. (format "Invalid DateTime string: %s" value)))))