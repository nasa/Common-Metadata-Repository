(ns cmr.search.test.services.parameter-converters.range-facet
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are3]]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.params :as p]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.search.services.parameters.converters.range-facet :as range-facet]))

(deftest range-facet->condition-test
  "Tests converting a range-facet string to an elastic query using conversions."
  (are3 [expected-min expected-max range-string]
    (is (= (qm/nested-condition
             :horizontal-data-resolutions
             (qm/numeric-range-condition
               :horizontal-data-resolutions.value expected-min expected-max))
           (range-facet/range-facet->condition :collection
                                               :horizontal-data-resolution-range
                                               range-string)))

    "Testing range-facet->condition using a string."
    1.0
    30.0
    "1 to 30 meters"

    "Testing range-facet->condition using a string and a conversion."
    1000.0
    30000.0
    "1 to 30 km"

    "Testing range-facet->condition using a string and a degree conversion."
    30000.0
    111195.0
    "30 km to 1 degree"))

(deftest parameter->condition-test
  "Tests converting a query parameter to a range-facet using both strings and vectors
   to an elastic query."
  (are3 [expected-min expected-max range]
    (is (= (qm/nested-condition
             :horizontal-data-resolutions
             (qm/numeric-range-condition
               :horizontal-data-resolutions.value expected-min expected-max))
           (p/parameter->condition nil
                                   :collection
                                   :horizontal-data-resolution-range
                                   range
                                   nil)))

    "Testing range facet parameter->condition using a string."
    1.0
    30.0
    "1 to 30 meters"

    "Testing range facet parameter->condition using a vector."
    1.0
    30.0
    ["1 to 30 meters"]))

(deftest parameter->condition-multiple-test
  "Tests converting multiple range facet query parameters to an elastic query."
  (are3 [expected1-min expected1-max expected2-min expected2-max range]
    (is (= (gc/or-conds [(qm/nested-condition
                           :horizontal-data-resolutions
                           (qm/numeric-range-condition
                             :horizontal-data-resolutions.value expected1-min expected1-max))
                         (qm/nested-condition
                             :horizontal-data-resolutions
                             (qm/numeric-range-condition
                               :horizontal-data-resolutions.value expected2-min expected2-max))])
           (p/parameter->condition nil
                                   :collection
                                   :horizontal-data-resolution-range
                                   range
                                   nil)))
    "Testing range facet parameter->condition using multiple parameters."
    1.0
    30.0
    30.0
    50.0
    ["1 to 30 meters" "30 to 50 meters"]))

(deftest validate-range-facet-str-test
  "Tests validating the range facet string parameter."
  (are3 [expected test-string]
    (is (= expected
           (range-facet/validate-range-facet-str test-string true)))

    "Testing a valid string with one unit."
    nil
    "30 to 30 meters"

    "Testing a valid string with both units."
    nil
    "30 meters to 40 km"

    "Testing a valid string with the + sign"
    nil
    "30 meters+"

    "Testing a valid string with the + sign and a different unit."
    nil
    "30 degrees +"

    "Testing a valid string with the & sign."
    nil
    "40 deg & above"

    "Testing an invalid unit"
    false
    "40 d & above"

    "Testing an invalid start of the string"
    false
    "some 40 to 30 meters"

    "Testing an invalid separator"
    false
    "40 o 30 meters"

    "Testing an invalid unit"
    false
    "40 to 30 feet"

    "Testing an invalid simbol"
    false
    "30 meters -"))

(deftest parse-range-test
  "Tests parsing the range facet string parameter."
  (are3 [expected test-string]
    (is (= expected
           (range-facet/parse-range test-string)))

    "Testing the normal string with 1 unit."
    [30.0 30.0]
    "30 to 30 meters"

    ""
    [30.0 40000.0]
    "30 meters to 40 km"

    ""
    [30.0 (Float/MAX_VALUE)]
    "30 meters+"

    ""
    [3335850.0 (Float/MAX_VALUE)]
    "30 degrees +"

    ""
    [4447800.0 (Float/MAX_VALUE)]
    "40 deg & above"))
