(ns cmr.search.data.datetime-helper
  "Contains helper functions for converting date time string to elastic date time string"
  (:require [clojure.string :as s]
            [clj-time.format :as f]))

(def earliest-echo-start-date "1582-10-15T00:00:00-0000")

(defn utc-time->elastic-time
  "Convert utc clj time to elasticsearch time string"
  [tm]
  (-> (f/unparse (f/formatters :date-time-no-ms) tm)
      (s/replace #"Z" "-0000")))

(defn parse-datetime
  "Convert the given string (in format like 2014-04-05T18:45:51Z) to Joda datetime.
  Returns nil for nil string, throws IllegalArgumentException for mal-formatted string.
  This is more strict than the parse-datetime function in terms of format validation."
  [s]
  (when-not (s/blank? s) (f/parse (f/formatters :date-time-no-ms) s)))

(defn datetime->string
  "Convert a Joda datetime to a datetime string formatted as :date-time-no-ms"
  [dt]
  (f/unparse (f/formatters :date-time-no-ms) dt))

