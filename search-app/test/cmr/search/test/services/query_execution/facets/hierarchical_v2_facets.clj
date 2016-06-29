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

(def find-applied-children
  "Var to call private function in hierarchical facets namespace."
  #'hv2/find-applied-children)

(deftest find-applied-children-test
  (let [field-hierarchy [:first :second :third :fourth :fifth]]
    (are3 [facets expected]
      (is (= expected (find-applied-children facets field-hierarchy false)))

      "Empty facets returns nil"
      {} nil

      "No children returns empty list"
      {:applied true :title "A"} []

      "Applied false are not included"
      {:applied true :title "A" :children
       [{:applied false :title "B" :children
         [{:applied false :title "C"}
          {:applied false :title "D" :children
           [{:applied false :title "E"}]}]}]}
      []

      "Multiple children at same level with all applied are included"
      {:applied true :title "A" :children
       [{:applied true :title "B" :children
         [{:applied true :title "C"}
          {:applied true :title "D" :children
           [{:applied true :title "E"}]}]}]}
      [[:second "B"] [:third "C"] [:third "D"] [:fourth "E"]]

      "Multiple children at same level with only one applied"
      {:applied true :title "A" :children
       [{:applied true :title "B" :children
         [{:applied false :title "C"}
          {:applied true :title "D" :children
           [{:applied true :title "E"}]}]}]}
      [[:second "B"] [:third "D"] [:fourth "E"]])
    (testing "Can include the top level term"
      (is (= [[:first "A"]]
             (find-applied-children {:applied true :title "A"} field-hierarchy true))))))
