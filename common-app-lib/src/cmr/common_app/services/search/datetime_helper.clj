(ns cmr.common-app.services.search.datetime-helper
  "Contains helper functions for converting date time string to elastic date time string"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.common.date-time-parser :as p]))

(def earliest-start-date
  "Earliest start date"
  "1582-10-15T00:00:00Z")

(def earliest-start-date-joda-time
  "Earliest start date in joda time"
  (p/parse-datetime earliest-start-date))

(def earliest-start-date-elastic-time
  "Earliest start time in elasticsearch time format"
  (s/replace earliest-start-date #"Z" "-0000"))

(defn utc-time->elastic-time
  "Convert utc clj time to elasticsearch time string."
  [tm]
  (-> (f/unparse (f/formatters :date-time) tm)
      (s/replace #"Z" "-0000")))

(defn datetime->string
  "Convert a Joda datetime to a datetime string formatted as :date-time-no-ms"
  [dt]
  (f/unparse (f/formatters :date-time-no-ms) dt))

