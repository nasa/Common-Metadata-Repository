(ns cmr.search.test.services.query-execution.facets.hierarchical-v2-facets
  "Unit tests for hierarchical-v2-facets namespace."
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]
            [cmr.common.util :refer [are3]]))

(deftest get-depth-for-hierarchical-field-test
  (testing "Defaults to 3 levels"
    (is (= 3 (hv2/get-depth-for-hierarchical-field {} :foo))))
  (are [sk-params expected-depth]
    (let [query-params (into {} (map (fn [[k v]]
                                       [(format "science_keywords[0][%s]" k) v]))
                                sk-params)]
      (= expected-depth (hv2/get-depth-for-hierarchical-field query-params :science-keywords)))

    {} 3
    {"category" "cat"} 3
    {"topic" "topic"} 4
    {"term" "term"} 5
    {"term" "term"
     "category" "cat"
     "topic" "topic"} 5
    {"variable_level_1" "vl1"} 6
    {"variable_level_2" "vl2"} 6
    {"variable_level_2" "vl2"
     "category" "cat"
     "topic" "topic"
     "term" "term"} 6
    {"variable_level_3" "vl3"} 6
    {"not_a_real_param" "foo"
     "topic" "topic"} 4))
