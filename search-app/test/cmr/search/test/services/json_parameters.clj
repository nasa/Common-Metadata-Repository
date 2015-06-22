(ns cmr.search.test.services.json-parameters
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters.conversion :as p]
            [cmr.search.models.query :as q]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.json-parameters.conversion :as jp]
            [cheshire.core :as json]))

(deftest json-parameters->query-test
  (testing "Empty parameters"
    (are [empty-string] (= (q/query {:concept-type :collection})
                           (jp/json-parameters->query :collection {} empty-string))
         "{}" "" nil))
  (testing "Combination of query and JSON parameters"
    (is (= (q/query {:concept-type :collection
                     :condition (q/string-condition :entry-title "ET")
                     :page-size 15})
           (jp/json-parameters->query :collection {:page-size 15, :include-facets true}
                                      (json/generate-string {:entry-title "ET"})))))
  (testing "Multiple nested JSON parameter conditions"
    (is (= (q/query {:concept-type :collection
                     :condition (gc/or-conds
                                  [(gc/and-conds [(q/string-condition :provider "bar")
                                                  (q/string-condition :entry-title "foo")])
                                   (gc/and-conds [(q/string-condition :entry-title "ET")
                                                  (q/string-condition :provider "soap")
                                                  (q/->NegatedCondition
                                                    (q/string-condition :provider "alpha"))])])})
           (jp/json-parameters->query
             :collection
             {}
             (json/generate-string {:or [{:entry-title "foo"
                                          :provider "bar"}
                                         {:provider "soap"
                                          :and [{:not {:provider "alpha"}}
                                                {:entry-title "ET"}]}]})))))
  (testing "Implicit ANDing of conditions"
    (is (= (q/query {:concept-type :collection
                     :condition (gc/and-conds [(q/string-condition :provider "bar")
                                               (q/string-condition :entry-title "foo")])})
           (jp/json-parameters->query :collection
                                      {}
                                      (json/generate-string {:entry-title "foo"
                                                             :provider "bar"}))))))

(deftest parameter->condition-test
  (testing "OR condition"
    (is (= (gc/or-conds [(q/string-condition :provider "foo")
                         (q/string-condition :entry-title "bar")])
           (jp/json-parameter->condition :collection :or
                                         [{:provider "foo"} {:entry-title "bar"}]))))
  (testing "AND condition"
    (is (= (gc/and-conds [(q/string-condition :provider "foo")
                          (q/string-condition :entry-title "bar")])
           (jp/json-parameter->condition :collection :and
                                         [{:provider "foo"} {:entry-title "bar"}]))))
  (testing "NOT condition"
    (is (= (q/->NegatedCondition (q/string-condition :provider "alpha"))
           (jp/json-parameter->condition :collection :not {:provider "alpha"}))))

  (testing "Nested conditions"
    (is (= (gc/or-conds [(gc/and-conds [(q/string-condition :provider "bar")
                                        (q/string-condition :entry-title "foo")])
                         (gc/and-conds [(q/string-condition :entry-title "ET")
                                        (q/string-condition :provider "soap")
                                        (q/->NegatedCondition
                                          (q/string-condition :provider "alpha"))])])
           (jp/json-parameter->condition :collection :or [{:entry-title "foo"
                                                           :provider "bar"}
                                                          {:provider "soap"
                                                           :and [{:not {:provider "alpha"}}
                                                                 {:entry-title "ET"}]}])))))
;; TODO tests
;     (testing "case-insensitive"
;       (is (= (q/string-condition :entry-title "bar" false false)
;              (p/parameter->condition :collection :entry-title "bar"
;                                      {:entry-title {:ignore-case "true"}}))))
;     (testing "pattern"
;       (is (= (q/string-condition :entry-title "bar*" false false)
;              (p/parameter->condition :collection :entry-title "bar*" {})))
;       (is (= (q/string-condition :entry-title "bar*" false true)
;              (p/parameter->condition :collection :entry-title "bar*"
;                                      {:entry-title {:pattern "true"}}))))))
