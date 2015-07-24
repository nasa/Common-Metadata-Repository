(ns cmr.search.data.datetime-helper
  "Contains helper functions for converting date time string to elastic date time string"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.common.date-time-parser :as p]))

(def earliest-echo-start-date
  "Earliest ECHO start date"
  "1582-10-15T00:00:00Z")

(def earliest-echo-start-date-joda-time
  "Earliest ECHO start date in joda time"
  (p/parse-datetime earliest-echo-start-date))

(def earliest-echo-start-date-elastic-time
  "Earliest ECHO start time in elasticsearch time format"
  (s/replace earliest-echo-start-date #"Z" "-0000"))

(defn utc-time->elastic-time
  "Convert utc clj time to elasticsearch time string."
  [tm]
  (-> (f/unparse (f/formatters :date-time) tm)
      (s/replace #"Z" "-0000")))

(defn datetime->string
  "Convert a Joda datetime to a datetime string formatted as :date-time-no-ms"
  [dt]
  (f/unparse (f/formatters :date-time-no-ms) dt))

