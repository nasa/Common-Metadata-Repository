(ns cmr.search.test.services.json-parameters
  "Testing functions used for parsing and generating query conditions for JSON queries."
  (:require [clojure.test :refer :all]
            [cmr.common.util :refer [are3]]
            [cmr.common-app.services.search.query-model :as q]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.search.services.json-parameters.conversion :as jp]
            [cheshire.core :as json]))

(deftest parse-json-query-test
  (testing "Empty JSON is valid"
    (is (= (q/query {:concept-type :collection
                     :result-features [:temporal-conditions]})
           (jp/parse-json-query :collection {} "{}"))))

  (testing "Combination of query and JSON parameters"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "ET")
                     :page-size 15
                     :result-features [:temporal-conditions]})
           (jp/parse-json-query :collection {:page-size 15, :include-facets true}
                                (json/generate-string {:condition {:entry_title "ET"}})))))
  (testing "Multiple nested JSON parameter conditions"
    (is (= (q/query {:concept-type :collection
                     :condition (gc/or-conds
                                  [(gc/and-conds [(q/string-condition :entry-title "foo")
                                                  (q/string-condition :provider "bar")])
                                   (gc/and-conds [(q/string-condition :provider "soap")
                                                  (q/string-condition :entry-title "ET")
                                                  (q/negated-condition
                                                    (q/string-condition :provider "alpha"))])])
                     :result-features [:temporal-conditions]})
           (jp/parse-json-query
             :collection
             {}
             (json/generate-string {:condition {:or [{:entry_title "foo"
                                                      :provider "bar"}
                                                     {:provider "soap"
                                                      :and [{:not {:provider "alpha"}}
                                                            {:entry_title "ET"}]}]}})))))
  (testing "Implicit ANDing of conditions"
    (is (= (q/query {:concept-type :collection
                     :condition (gc/and-conds [(q/string-condition :entry-title "foo")
                                               (q/string-condition :provider "bar")])
                     :result-features [:temporal-conditions]})
           (jp/parse-json-query :collection {} (json/generate-string {:condition {:entry_title "foo"
                                                                                  :provider "bar"}}))))))

(deftest parse-json-condition-test
  (testing "OR condition"
    (is (= (gc/or-conds [(q/string-condition :provider "foo")
                         (q/string-condition :entry-title "bar")])
           (jp/parse-json-condition :collection :or [{:provider "foo"} {:entry-title "bar"}]))))
  (testing "AND condition"
    (is (= (gc/and-conds [(q/string-condition :provider "foo")
                          (q/string-condition :entry-title "bar")])
           (jp/parse-json-condition :collection :and [{:provider "foo"} {:entry-title "bar"}]))))
  (testing "NOT condition"
    (is (= (q/negated-condition (q/string-condition :provider "alpha"))
           (jp/parse-json-condition :collection :not {:provider "alpha"}))))

  (testing "Nested conditions"
    (is (= (gc/or-conds [(gc/and-conds [(q/string-condition :entry-title "foo")
                                        (q/string-condition :provider "bar")])
                         (gc/and-conds [(q/string-condition :provider "soap")
                                        (q/string-condition :entry-title "ET")
                                        (q/->NegatedCondition
                                         (q/string-condition :provider "alpha"))])])
           (jp/parse-json-condition :collection :or [{:entry-title "foo"
                                                      :provider "bar"}
                                                     {:provider "soap"
                                                      :and [{:not {:provider "alpha"}}
                                                            {:entry-title "ET"}]}]))))

  (testing "case-insensitive"
    (is (= (q/string-condition :entry-title "bar" false false)
           (jp/parse-json-condition :collection :entry-title {:value "bar" :ignore-case true :pattern false})))
    (is (= (q/string-condition :entry-title "bar" true false)
           (jp/parse-json-condition :collection :entry-title {:value "bar" :ignore-case false :pattern false}))))
  (testing "pattern"
    (is (= (q/string-condition :entry-title "bar*" false false)
           (jp/parse-json-condition :collection :entry-title {:value "bar*" :ignore-case true :pattern false})))
    (is (= (q/string-condition :entry-title "bar*" false true)
           (jp/parse-json-condition :collection :entry-title {:value "bar*" :ignore-case true
                                                              :pattern true})))))

(deftest parse-json-condition-range-facet->condition-test
  "Tests converting a range-facet string to an elastic query using conversions."
  (are3 [expected-min expected-max range-string]
    (is (= (q/nested-condition
             :horizontal-data-resolutions
             (q/numeric-range-condition
               :horizontal-data-resolutions.value expected-min expected-max))
           (jp/parse-json-condition :collection :horizontal-data-resolution-range range-string)))

    "Testing range-facet->condition using a string."
    1.0
    30.0
    "1 to 30 meters"))
