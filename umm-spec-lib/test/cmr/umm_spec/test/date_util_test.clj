(ns cmr.umm-spec.test.date-util-test
  "Tests for cmr.umm-spec.date-util functions"
  (:require
   [clj-time.format :as f]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.date-util :as date]))

(deftest sanitize-and-parse-dates
  (are3 [d sanitize? expected]
        (is (= (date/sanitize-and-parse-date d sanitize?)
               expected))

        "year-month"
        "2003/08" true "2003-08-01T00:00:00.000Z"

        "year"
        "2003" true "2003-01-01T00:00:00.000Z"

        "year-month-day"
        "2014/07/24" true "2014-07-24T00:00:00.000Z"

        "date/time"
        "2016/09/27T13:34:03Z" true "2016-09-27T13:34:03.000Z"

        "No sanitization"
        "2003/08" false "2003/08"))
