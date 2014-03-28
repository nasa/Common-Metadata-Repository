(ns cmr.common.test.date-time-parser
  "Contains tests for date-time-parser"
  (:require [clojure.test :refer :all]
            [cmr.common.date-time-parser :as p]
            [clj-time.core :as t]))

(deftest test-parse-valid-date-time
  (testing "Parse valid date times with milliseconds"
    (let [values ["1986-10-14T04:03:27.456Z"
                  "1986-10-14T04:03:27.456"
                  "1986-10-14T4:3:27.456"   ;; single digit is valid
                  "1986-10-14T04:03:27.456-05:00"]
          expected (t/date-time 1986 10 14 4 3 27 456)]
      (doseq [value values]
        (let [actual (p/string->datetime value)]
          (is (= expected actual))))))
  (testing "Parse valid date time without milliseconds"
    (let [values ["1986-10-14T04:03:27Z"
                  "1986-10-14T04:03:27"
                  "1986-10-14T04:03:27-05:00"
                  "1986-10-14T4:3:27"]
          expected (t/date-time 1986 10 14 4 3 27 0)]
      (doseq [value values]
        (let [actual (p/string->datetime value)]
          (is (= expected actual)))))))

(deftest test-parse-invalid-date-time
  (testing "Parse invalid date times should throw exception"
    (let [values ["invalid-date-time"
                  "1998"
                  "1986-10-14"           ;; just date is invalid
                  "F1986-10-14T04:03:27"
                  "-1986-10-14T04:03:27"
                  "1986-10-14 04:03:27"  ;; missing separator is invalid
                  "1986-10-14Q04:03:27"  ;; wrong separator is invalid
                  "1986-10-14T04:03:"
                  "1986-13-14T04:03:27"  ;; invalid month
                  "1986-10-34T04:03:27"  ;; invalid day
                  "1986-10-14T24:03:27"  ;; invalid hour
                  "1986-10-14T04:61:27"  ;; invalid minute
                  "1986-10-14T04:03:61"  ;; invalid second
                  ]]
      (doseq [value values]
        (is (thrown? Exception (p/string->datetime value)))))))
