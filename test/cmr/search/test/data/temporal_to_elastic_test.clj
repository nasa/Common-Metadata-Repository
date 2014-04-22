(ns cmr.search.test.data.temporal-to-elastic-test
  "Contains tests to verify current-end-date calculation."
  (:require [clojure.test :refer :all]
            [cmr.search.data.datetime-helper :as h]
            [cmr.search.data.temporal-to-elastic :as t2e]))

(deftest current-end-date-test
  (are [expected current-year end-date start-day end-day end-year]
       (= (h/string->datetime expected)
          (t2e/current-end-date
            current-year
            (h/string->datetime end-date)
            start-day
            end-day
            end-year))
       "1986-10-14T04:03:27Z" 1986 "1986-10-14T04:03:27Z" 1 nil 1986
       "1985-12-31T23:59:59Z" 1985 "1986-10-14T04:03:27Z" 1 nil 1986
       "1985-12-26T00:00:00Z" 1985 "1986-10-14T04:03:27Z" 1 360 1986
       "1986-10-14T04:03:27Z" 1986 "1986-10-14T04:03:27Z" 1 360 1986
       "1986-01-10T00:00:00Z" 1985 "1986-10-14T04:03:27Z" 360 10 1986
       "1986-10-14T04:03:27Z" 1986 "1986-10-14T04:03:27Z" 360 10 1986))