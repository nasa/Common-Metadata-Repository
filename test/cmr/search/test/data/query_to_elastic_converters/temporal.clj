(ns cmr.search.test.data.query-to-elastic-converters.temporal
  "Contains tests to verify current-end-date calculation."
  (:require [clojure.test :refer :all]
            [cmr.common.date-time-parser :as parser]
            [cmr.search.data.complex-to-simple-converters.temporal :as t2e]))

(deftest current-end-date-test
  "Test end-date is calculated correctly for current year during periodic date time conversion."
  (are [expected current-year end-date start-day end-day end-year]
       (= (parser/parse-datetime expected)
          (t2e/current-end-date
            current-year
            (parser/parse-datetime end-date)
            start-day
            end-day
            end-year))
       "1986-10-14T04:03:27Z" 1986 "1986-10-14T04:03:27Z" 1 nil 1986
       "1985-12-31T23:59:59Z" 1985 "1986-10-14T04:03:27Z" 1 nil 1986
       "1985-12-26T00:00:00Z" 1985 "1986-10-14T04:03:27Z" 1 360 1986
       "1986-10-14T04:03:27Z" 1986 "1986-10-14T04:03:27Z" 1 360 1986
       "1986-01-10T00:00:00Z" 1985 "1986-10-14T04:03:27Z" 360 10 1986
       "1986-10-14T04:03:27Z" 1986 "1986-10-14T04:03:27Z" 360 10 1986))