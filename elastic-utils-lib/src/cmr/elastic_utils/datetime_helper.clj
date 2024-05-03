(ns cmr.elastic-utils.datetime-helper
  "Contains helper functions for converting date time string to elastic date time string"
  (:require
   [clj-time.format :as time-format]
   [clojure.string :as string]
   [cmr.common.date-time-parser :as time-parser]))

(def earliest-start-date
  "Earliest start date"
  "1582-10-15T00:00:00Z")

(def earliest-start-date-joda-time
  "Earliest start date in joda time"
  (time-parser/parse-datetime earliest-start-date))

(def earliest-start-date-elastic-time
  "Earliest start time in elasticsearch time format"
  (string/replace earliest-start-date #"Z" "-0000"))

(defn utc-time->elastic-time
  "Convert utc clj time to elasticsearch time string."
  [tm]
  (if (string? tm)
    (string/replace tm #"Z" "-0000")
    (-> (time-format/formatters :date-time)
        (time-format/unparse tm)
        (string/replace #"Z" "-0000"))))

(defn datetime->string
  "Convert a Joda datetime to a datetime string formatted as :date-time-no-ms"
  [dt]
  (time-format/unparse (time-format/formatters :date-time-no-ms) dt))
