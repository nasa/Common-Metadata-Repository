(ns cmr.common.test.date-time-range-parser
  "Contains tests for date-time-range-parser"
  (:require [clojure.test :refer :all]
            [cmr.common.date-time-range-parser :as p]
            [clj-time.core :as t])
  (:import clojure.lang.ExceptionInfo))

(defn- to-utc
  "Get equivalent date-time in UTC"
  [date-time]
  (when date-time
    (t/to-time-zone date-time org.joda.time.DateTimeZone/UTC)))

(deftest parse-datetime-range
  (testing "valid datetime ranges"
    (are [string start end]
         (let [interval (p/parse-datetime-range string)
               range-start (:start-date interval)
               range-end (:end-date interval)]
           (and (= start (to-utc range-start))
                (= end (to-utc range-end))))

         "1987-01-01T00:00:00.000Z,1990-01-01T00:00:00.000Z"
         (t/date-time 1987 1 1 0 0 0) (t/date-time 1990 1 1 0 0 0)

         ",2001-02-01T10:30:00Z"
         nil (t/date-time 2001 2 1 10 30 0)

         "2005-02-01T5:30:00Z,"
         (t/date-time 2005 2 1 5 30 0) nil

         "1987-01-01T00:00:00.000Z/1990-01-01T00:00:00.000Z"
         (t/date-time 1987 1 1 0 0 0) (t/date-time 1990 1 1 0 0 0)

         "1987-01-01T00:00:00.000Z/P1Y2M10DT2H30M"
         (t/date-time 1987 1 1 0 0 0) (t/date-time 1988 3 11 2 30 0)

         ;; This test fails in CI due to the issue descirbed in CMR-1737
         ;; "P1Y2M10DT2H30M/2008-05-11T15:30:00Z"
         ;; (t/date-time 2007 3 1 14 0 0) (t/date-time 2008 5 11 15 30 0)

         ;; This tests fail locally assuming the code is run on a machine
         ;; which is using Eastern Time Zone. But succeeds on a machine using UTC.
         ;; "1987-03-02T00:00:00.000Z/P1Y2M10DT2H30M"
         ;; (t/date-time 1987 3 2 0 0 0) (t/date-time 1988 5 12 2 30 0)


         "/2001-02-01T10:30:00Z"
         nil (t/date-time 2001 2 1 10 30 0)

         "2005-02-01T5:30:00Z/"
         (t/date-time 2005 2 1 5 30 0) nil))

  (testing "invalid datetime ranges"
    (are [string error-re]
         (try
           (p/parse-datetime-range string)
           false
           (catch clojure.lang.ExceptionInfo e
             (let [{:keys [type errors]} (ex-data e)]
               (and (= type :invalid-data)
                    (= 1 (count errors))
                    (re-matches error-re (first errors))))))
         "1987-01-01T00:00:00.000Z|1990-01-01T00:00:00.000Z" #".*is not a valid datetime.*"
         "1987-01-01T00:00:00.000Z-2000-01-01T00:00:00.000Z" #".*is not a valid datetime.*"
         "1987-01-0100:00:00.000Z,2000-01-01T00:00:00.000Z" #".*is not a valid datetime.*"
         "1987-01-01T00:00:00.000Z/2000-11-T00:00:00.000Z" #".*is not a valid date-range.*"
         "1987-01-01T00:00:00.000Z/1Y2M10DT2H30M" #".*is not a valid date-range.*")))