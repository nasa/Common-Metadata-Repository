(ns cmr.common.test.date-time-parser
  "Contains tests for date-time-parser"
  (:require [clojure.test :refer :all]
            [cmr.common.date-time-parser :as p]
            [cmr.common.services.messages :as msg]
            [clj-time.core :as t])
  (:import clojure.lang.ExceptionInfo))

(deftest parse-datetime
  (testing "valid datetimes"
    (are [string date] (= date (p/parse-datetime string))
         "1986-10-14T04:03:27.456Z" (t/date-time 1986 10 14 4 3 27 456)
         "1986-10-14T04:03:27.45Z" (t/date-time 1986 10 14 4 3 27 450)
         "1986-10-14T04:03:27.4Z" (t/date-time 1986 10 14 4 3 27 400)
         "1986-10-14T04:03:27Z" (t/date-time 1986 10 14 4 3 27)
         "1986-10-14T04:03:27.456" (t/date-time 1986 10 14 4 3 27 456)
         "1986-10-14T04:03:27.45" (t/date-time 1986 10 14 4 3 27 450)
         "1986-10-14T04:03:27.4" (t/date-time 1986 10 14 4 3 27 400)
         "1986-10-14T04:03:27" (t/date-time 1986 10 14 4 3 27)
         "1986-10-14T04:03:27-00:00" (t/date-time 1986 10 14 4 3 27)
         "1986-10-14T04:03:27-01:00" (t/date-time 1986 10 14 5 3 27)
         "1986-10-14T04:03:27.123-00:00" (t/date-time 1986 10 14 4 3 27 123)
         "1986-10-14T04:03:27.12-00:00" (t/date-time 1986 10 14 4 3 27 120)
         "1986-10-14T04:03:27.1-00:00" (t/date-time 1986 10 14 4 3 27 100)))
  (testing "invalid datetimes"
    (are [string]
         (try
           (p/parse-datetime string)
           false
           (catch ExceptionInfo e
             (let [{:keys [type errors]} (ex-data e)]
               (and (= type :invalid-data)
                    (= 1 (count errors))
                    (re-matches #".*is not a valid datetime.*" (first errors))))))
         "foo"
         "1986-10-14"
         "1986-10-14T24:00:00")))

(deftest parse-date
  (testing "valid date"
    (is (= (t/date-time 1986 10 14)
       (p/parse-date "1986-10-14"))))
  (testing "invalid dates"
    (are [string]
         (try
           (p/parse-date string)
           false
           (catch ExceptionInfo e
             (let [{:keys [type errors]} (ex-data e)]
               (and (= type :invalid-data)
                    (= 1 (count errors))
                    (re-matches #".*is not a valid date.*" (first errors))))))
         "foo"
         "1986-10-44"
         "--")))


(deftest parse-time
  (testing "valid times"
    (are [string date] (= date (p/parse-time string))
         "04:03:27.456Z" (t/date-time 1970 1 1 4 3 27 456)
         "04:03:27.45Z" (t/date-time 1970 1 1 4 3 27 450)
         "04:03:27.4Z" (t/date-time 1970 1 1 4 3 27 400)
         "04:03:27Z" (t/date-time 1970 1 1 4 3 27)
         "04:03:27.456" (t/date-time 1970 1 1 4 3 27 456)
         "04:03:27.45" (t/date-time 1970 1 1 4 3 27 450)
         "04:03:27.4" (t/date-time 1970 1 1 4 3 27 400)
         "04:03:27" (t/date-time 1970 1 1 4 3 27)
         "04:03:27-00:00" (t/date-time 1970 1 1 4 3 27)
         "04:03:27-01:00" (t/date-time 1970 1 1 5 3 27)
         "04:03:27.123-00:00" (t/date-time 1970 1 1 4 3 27 123)
         "04:03:27.12-00:00" (t/date-time 1970 1 1 4 3 27 120)
         "04:03:27.1-00:00" (t/date-time 1970 1 1 4 3 27 100)))
  (testing "invalid times"
    (are [string]
         (try
           (p/parse-time string)
           false
           (catch ExceptionInfo e
             (let [{:keys [type errors]} (ex-data e)]
               (and (= type :invalid-data)
                    (= 1 (count errors))
                    (re-matches #".*is not a valid time.*" (first errors))))))
         "foo"
         "1986-10-14"
         "24:00:00")))


