(ns cmr.search.test.services.query-execution.facets.temporal-facets
  "Unit tests for facets links helper namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.temporal-facets :as temporal-facets]))

(deftest parse-date-test
  (util/are3 [expected time-interval datetime-string]
    (is (= expected (temporal-facets/parse-date datetime-string time-interval)))

    "Year 2010"
    "2010" :year "2010-12-30T10:15:35+000"

    "Month 12"
    "12" :month "2010-12-30T10:15:35+000"

    "Day 30"
    "30" :day "2010-12-30T10:15:35+000"

    "Hour 10"
    "10" :hour "2010-12-30T10:15:35+000"))

(deftest query-params->time-interval-test
  (util/are3 [expected query-params]
    (is (= expected (temporal-facets/query-params->time-interval query-params)))

    "No temporal facet parameters"
    :year {"foo" "bar"}

    "Single temporal-facet parameter with year"
    :month {"temporal_facet[0][year]" "1537"}

    "Single temporal-facet parameter with month"
    :month {"temporal_facet[0][year]" "1537"
            "temporal_facet[0][month]" "8"}

    "Temporal facet in the value of a query parameter is ignored"
    :year {"foo" "temporal_facet[0][year]=1537"}

    "Multiple temporal-facet parameters"
    :month {"temporal_facet[5][year]" 2010
            "temporal_facet[5][month]" 10
            "temporal_facet[2][year]" 2013}))
