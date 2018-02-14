(ns cmr.search.test.services.query-execution.facets.hierarchical-v2-facets
  "Unit tests for hierarchical-v2-facets namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]))

(deftest get-depth-for-hierarchical-field-test
  (testing "Defaults to 3 levels"
    (is (= 3 (hv2/get-depth-for-hierarchical-field {} :foo))))
  (are [sk-params expected-depth]
    (let [query-params (into {} (map (fn [[k v]]
                                       [(format "science_keywords_h[0][%s]" k) v]))
                                sk-params)]
      (= expected-depth (hv2/get-depth-for-hierarchical-field query-params :science-keywords-h)))

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
    (util/are3 [facets expected]
      (is (= expected (find-applied-children facets field-hierarchy false)))

      "Empty facets returns nil"
      {} nil

      "No children returns empty list when include-root is false"
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

    (testing "Root is included when include-root is true and applied"
      (is (= [[:first "A"]]
             (find-applied-children {:applied true :title "A"} field-hierarchy true))))
    (testing "Root is not included when include-root is true but applied is false"
      (is (= nil
             (find-applied-children {:applied false :title "A"} field-hierarchy true))))))

(deftest get-indexes-in-params
  (util/are3 [query-params expected]
    (is (= expected (#'hv2/get-indexes-in-params query-params "foo" "alpha" "found")))

    "Single matching param and value"
    {"foo[0][alpha]" "found"} #{0}

    "Multiple matching params and values"
    {"foo[0][alpha]" "found"
     "foo[1][alpha]" "found"} #{0 1}

    "Different base field is not matched"
    {"bar[0][alpha]" "found"} #{}

    "Different value is not matched"
    {"foo[0][alpha]" "not-found"} #{}

    "Different subfield is not matched"
    {"foo[0][beta]" "found"} #{}

    "Values are compared case insensitively"
    {"foo[0][alpha]" "FoUnD"} #{0}

    "Large index"
    {"foo[1234567890][alpha]" "found"} #{1234567890}

    "Empty query-params OK"
    {} #{}

    "Nil query-params OK"
    nil #{}

    "Combination of scenarios"
    {"foo[0][alpha]" "found"
     "foo[1][alpha]" "found"
     "bar[0][alpha]" "found"
     "bar[2][alpha]" "found"
     "foo[3][beta]" "found"
     "foo[4][alpha]" "not-found"
     "foo[1234567890][alpha]" "FOUNd"} #{0 1 1234567890}))

(def has-siblings?
  "Var to call private has-siblings? function in hierarchical facets namespace."
  #'hv2/has-siblings?)

(deftest has-siblings?-test
  (util/are3 [query-params expected]
    (is (= expected
           (has-siblings? query-params "foo" "parent-alpha" "parent-found" "alpha" "me")))

    "Single matching sibling"
    {"foo[0][alpha]" "sibling"
     "foo[0][parent-alpha]" "parent-found"} true

    "Single matching sibling, index does not matter"
    {"foo[123][alpha]" "sibling"
     "foo[123][parent-alpha]" "parent-found"} true

    "Parent value matches are case insensitive"
    {"foo[0][alpha]" "sibling"
     "foo[0][parent-alpha]" "PAREnt-FOUnd"} true

    "Current value is in query-params, but no siblings."
    {"foo[0][alpha]" "me"
     "foo[0][parent-alpha]" "parent-found"} false

    "Current value is matched case insensitively and finds no siblings"
    {"foo[0][alpha]" "ME"
     "foo[0][parent-alpha]" "parent-found"} false

    "Current value is in query-params, and has siblings."
    {"foo[0][alpha]" "me"
     "foo[1][alpha]" "sibling"
     "foo[1][parent-alpha]" "parent-found"
     "foo[0][parent-alpha]" "parent-found"} true

    "Multiple siblings"
    {"foo[0][alpha]" "sibling1"
     "foo[1][alpha]" "sibling2"
     "foo[1][parent-alpha]" "parent-found"
     "foo[0][parent-alpha]" "parent-found"} true

    "Different parent value"
    {"foo[0][alpha]" "not-sibling"
     "foo[0][parent-alpha]" "different-parent"} false

    "Different parent subfield"
    {"foo[0][alpha]" "not-sibling"
     "foo[0][parent-beta]" "parent-found"} false

    "Different parent index."
    {"foo[1][alpha]" "sibling"
     "foo[0][parent-alpha]" "parent-found"} false

    "Current value is in query-params, there's another parameter for the same subfield, but no
    parent."
    {"foo[0][alpha]" "me"
     "foo[1][alpha]" "sibling"
     "foo[0][parent-alpha]" "parent-found"} false

    "Large index"
    {"foo[1234567890][alpha]" "sibling"
     "foo[1234567890][parent-alpha]" "parent-found"} true

    "Empty query-params"
    {} false

    "Nil query-params"
    nil false))

(deftest get-field-hierarchy-test
  (let [science-keywords-fields [:category :topic :term :variable-level-1 :variable-level-2
                                 :variable-level-3]
        variable-fields [:measurement :variable]]
    (util/are3 [field query-params expected]
      (is (= expected (hv2/get-field-hierarchy field query-params)))

      "Science keywords"
      :science-keywords {"science_keywords_h[0][category]" "foo"} science-keywords-fields

      "Science keywords Humanized"
      :science-keywords-h {} science-keywords-fields

      "Variables"
      :variables {} variable-fields

      "Temporal facets not selected"
      :temporal-facet {} [:year]

      "Temporal facets year selected"
      :temporal-facet {"temporal_facet[0][year]" "1537"} [:year :month]

      "Temporal facets year and month selected"
      :temporal-facet {"temporal_facet[0][year]" "1537"
                       "temporal_facet[0][month]" "8"}
      [:year :month])))
