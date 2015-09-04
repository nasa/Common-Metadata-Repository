(ns cmr.common.test.time-util
  "Contains utilities for testing with time. Positions in time can be represented by a simple number
  referred to here as N. Any particular N value is offset from a base time by N units. N can be
  negative or positive"
  (:require [clj-time.core :as t]))

(def base-time
  "The base time everything else is based off of. N = 0"
  (t/date-time 2000))

(def n->period
  "A function for converting an N value to a period of time"
  t/months)

(defn n->date-time
  "Converts N value to a date time."
  [n]
  (when n (t/plus base-time (n->period n))))

(defn n->date-time-string
  "Converts N value to a date time string"
  [n]
  (when n (str (n->date-time n))))

