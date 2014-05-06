(ns cmr.search.data.datetime-helper
  "Contains helper functions for converting date time string to elastic date time string"
  (:require [clojure.string :as s]
            [clj-time.format :as f]))

(def earliest-echo-start-date "1582-10-15T00:00:00-0000")

(defn utc-time->elastic-time
  "Convert utc clj time to elasticsearch time string.
  We dropped the milliseconds in datetime to make the code simpler
  as there is no use case for searching within milliseconds."
  [tm]
  (-> (f/unparse (f/formatters :date-time-no-ms) tm)
      (s/replace #"Z" "-0000")))

(defn datetime->string
  "Convert a Joda datetime to a datetime string formatted as :date-time-no-ms"
  [dt]
  (f/unparse (f/formatters :date-time-no-ms) dt))

