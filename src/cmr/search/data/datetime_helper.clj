(ns cmr.search.data.datetime-helper
  "Contains helper functions for converting date time string to elastic date time string"
  (:require [clojure.string :as s]))

(def earliest-echo-start-date "1582-10-15T00:00:00-0000")

(defn utc-time->elastic-time
  "Convert utc time string to elasticsearch time string"
  [tm]
  (s/replace tm #"Z" "-0000"))
