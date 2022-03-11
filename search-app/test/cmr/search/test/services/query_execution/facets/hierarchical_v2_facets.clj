(ns cmr.search.test.services.query-execution.facets.hierarchical-v2-facets
  "Unit tests for hierarchical-v2-facets namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]))

(deftest get-depth-for-hierarchical-field-test
  (testing "Defaults to 3 levels"
    (is (= 3 (hv2/get-depth-for-hierarchical-field {} :foo))))
  (are3 [sk-params expected-depth]
    (let [query-params (into {} (map (fn [[k v]]
                                       [(format "science_keywords_h[0][%s]" k) v]))
                                sk-params)]
      (= expected-depth (hv2/get-depth-for-hierarchical-field query-params :science-keywords-h)))

    "Test the default which is 3 levels."
    {} 3

    "Tests the first science keyword level of category."
    {"category" "cat"} 3

    "Tests the second science keyword level of topic."
    {"topic" "topic"} 4

    "Tests the third science keyword level of term."
    {"term" "term"} 5

    "Tests the first three science keywords."
    {"term" "term"
     "category" "cat"
     "topic" "topic"}
    5

    "Tests the fourth science keyword."
    {"variable_level_1" "vl1"} 6

    "Tests the fifth science keyword."
    {"variable_level_2" "vl2"} 7

    "Tests the first three plus the fifth science keywords."
    {"variable_level_2" "vl2"
     "category" "cat"
     "topic" "topic"
     "term" "term"}
    7

    "Tests the 6th science keyword. There are 7 total."
    {"variable_level_3" "vl3"} 7

    "Tests the 2nd science keyword plus a bogus term."
    {"not_a_real_param" "foo"
     "topic" "topic"}
    4))

(def find-applied-children
  "Var to call private function in hierarchical facets namespace."
  #'hv2/find-applied-children)

(deftest find-applied-children-test
  (let [field-hierarchy [:first :second :third :fourth :fifth]]
    (are3 [facets expected]
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
  (are3 [query-params expected]
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
  (are3 [query-params expected]
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
                                 :variable-level-3 :detailed-variable]
        variable-fields [:measurement :variable]]
    (are3 [field query-params expected]
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
      [:year :month :day]

      "Temporal facets year, month, and day selected"
      :temporal-facet {"temporal_facet[0][year]" "1537"
                       "temporal_facet[0][month]" "8"
                       "temporal_facet[0][day]" "15"}
      [:year :month :day])))

(def expected-nested-facet-aggregations-for-science-keywords
  {:nested {:path :science-keywords-humanized},
   :aggs
   {:category
    {:terms {:field "science-keywords-humanized.category", :size 1},
     :aggs
     {:coll-count {:reverse_nested {},
                   :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
      :topic
      {:terms {:field "science-keywords-humanized.topic", :size 1},
       :aggs
       {:coll-count {:reverse_nested {},
                     :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
        :term
        {:terms {:field "science-keywords-humanized.term", :size 1},
         :aggs
         {:coll-count {:reverse_nested {},
                       :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
          :detailed-variable
          {:terms {:field "science-keywords-humanized.detailed-variable", :size 1},
           :aggs
           {:coll-count {:reverse_nested {},
                         :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}}}},
          :variable-level-1
          {:terms {:field "science-keywords-humanized.variable-level-1", :size 1},
           :aggs
           {:coll-count {:reverse_nested {},
                         :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
            :detailed-variable
            {:terms {:field "science-keywords-humanized.detailed-variable", :size 1},
             :aggs {:coll-count {:reverse_nested {},
                                 :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}}}},
            :variable-level-2
            {:terms {:field "science-keywords-humanized.variable-level-2", :size 1},
             :aggs
             {:coll-count {:reverse_nested {},
                           :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
              :detailed-variable
              {:terms {:field "science-keywords-humanized.detailed-variable", :size 1},
                       :aggs {:coll-count {:reverse_nested {},
                                           :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}}}},
              :variable-level-3
              {:terms {:field "science-keywords-humanized.variable-level-3", :size 1},
               :aggs
               {:coll-count {:reverse_nested {},
                             :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
                :detailed-variable
                {:terms {:field "science-keywords-humanized.detailed-variable", :size 1},
                 :aggs
                 {:coll-count {:reverse_nested {},
                               :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}}}}}}}}}}}}}}}}}})

(def expected-nested-facet-aggregations-for-platforms2
  {:nested {:path :platforms2-humanized},
   :aggs
   {:basis
    {:terms {:field "platforms2-humanized.basis", :size 1},
     :aggs
     {:coll-count {:reverse_nested {},
                   :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
      :category
      {:terms {:field "platforms2-humanized.category", :size 1},
       :aggs
       {:coll-count {:reverse_nested {},
                     :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
        :short-name
        {:terms {:field "platforms2-humanized.short-name", :size 1},
         :aggs
         {:coll-count {:reverse_nested {},
                       :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}}}},
        :sub-category
        {:terms {:field "platforms2-humanized.sub-category", :size 1},
         :aggs
         {:coll-count {:reverse_nested {},
                       :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}},
          :short-name
          {:terms {:field "platforms2-humanized.short-name", :size 1},
           :aggs
           {:coll-count {:reverse_nested {},
                         :aggs {:concept-id {:terms {:field :concept-id, :size 1}}}}}}}}}}}}}})

(deftest nested-facet-test
  "This function tests the nested aggregations that are created for getting facets from
   elastic search."
  (testing "nested facets with science-keywords"
    (is (= expected-nested-facet-aggregations-for-science-keywords
           (hv2/nested-facet :science-keywords-humanized 1))))
  (testing "nested facets with platforms2"
    (is (= expected-nested-facet-aggregations-for-platforms2
           (hv2/nested-facet :platforms2-humanized 1)))))

(def science-keywords-full-search-aggregations
  {:science-keywords-h
   {:doc_count 1,
    :category
    {:doc_count_error_upper_bound 0,
     :sum_other_doc_count 0,
     :buckets
     [{:key "Earth Science",
       :doc_count 1,
       :topic
       {:doc_count_error_upper_bound 0,
        :sum_other_doc_count 0,
        :buckets
        [{:key "Topic1",
          :doc_count 1,
          :term
          {:doc_count_error_upper_bound 0,
           :sum_other_doc_count 0,
           :buckets
           [{:key "Term1",
             :doc_count 1,
             :variable-level-1
             {:doc_count_error_upper_bound 0,
              :sum_other_doc_count 0,
              :buckets
              [{:key "Level1-1",
                :doc_count 1,
                :detailed-variable
                {:doc_count_error_upper_bound 0,
                 :sum_other_doc_count 0,
                 :buckets
                 [{:key "Detail1",
                   :doc_count 1,
                   :coll-count
                   {:doc_count 1,
                    :concept-id
                    {:doc_count_error_upper_bound 0,
                     :sum_other_doc_count 0,
                     :buckets
                     [{:key "C1200000008-PROV1",
                       :doc_count 1}]}}}]},
                :coll-count
                {:doc_count 1,
                 :concept-id
                 {:doc_count_error_upper_bound 0,
                  :sum_other_doc_count 0,
                  :buckets
                  [{:key "C1200000008-PROV1",
                    :doc_count 1}]}},
                :variable-level-2
                {:doc_count_error_upper_bound 0,
                 :sum_other_doc_count 0,
                 :buckets
                 [{:key "Level1-2",
                   :doc_count 1,
                   :detailed-variable
                   {:doc_count_error_upper_bound 0,
                    :sum_other_doc_count 0,
                    :buckets
                    [{:key "Detail1",
                      :doc_count 1,
                      :coll-count
                      {:doc_count 1,
                       :concept-id
                       {:doc_count_error_upper_bound 0,
                        :sum_other_doc_count 0,
                        :buckets
                        [{:key "C1200000008-PROV1",
                          :doc_count 1}]}}}]},
                   :coll-count
                   {:doc_count 1,
                    :concept-id
                    {:doc_count_error_upper_bound 0,
                     :sum_other_doc_count 0,
                     :buckets
                     [{:key "C1200000008-PROV1",
                       :doc_count 1}]}},
                   :variable-level-3
                   {:doc_count_error_upper_bound 0,
                    :sum_other_doc_count 0,
                    :buckets
                    [{:key "Level1-3",
                      :doc_count 1,
                      :detailed-variable
                      {:doc_count_error_upper_bound 0,
                       :sum_other_doc_count 0,
                       :buckets
                       [{:key "Detail1",
                         :doc_count 1,
                         :coll-count
                         {:doc_count 1,
                          :concept-id
                          {:doc_count_error_upper_bound 0,
                           :sum_other_doc_count 0,
                           :buckets
                           [{:key "C1200000008-PROV1",
                             :doc_count 1}]}}}]},
                      :coll-count
                      {:doc_count 1,
                       :concept-id
                       {:doc_count_error_upper_bound 0,
                        :sum_other_doc_count 0,
                        :buckets
                        [{:key "C1200000008-PROV1",
                          :doc_count 1}]}}}]}}]}}]},
             :detailed-variable
             {:doc_count_error_upper_bound 0,
              :sum_other_doc_count 0,
              :buckets
              [{:key "Detail1",
                :doc_count 1,
                :coll-count
                {:doc_count 1,
                 :concept-id
                 {:doc_count_error_upper_bound 0,
                  :sum_other_doc_count 0,
                  :buckets
                  [{:key "C1200000008-PROV1",
                    :doc_count 1}]}}}]},
             :coll-count
             {:doc_count 1,
              :concept-id
              {:doc_count_error_upper_bound 0,
               :sum_other_doc_count 0,
               :buckets
               [{:key "C1200000008-PROV1",
                 :doc_count 1}]}}}]},
          :coll-count
          {:doc_count 1,
           :concept-id
           {:doc_count_error_upper_bound 0,
            :sum_other_doc_count 0,
            :buckets
            [{:key "C1200000008-PROV1",
              :doc_count 1}]}}}]},
       :coll-count
       {:doc_count 1,
        :concept-id
        {:doc_count_error_upper_bound 0,
         :sum_other_doc_count 0,
         :buckets
         [{:key "C1200000008-PROV1", :doc_count 1}]}}}]}}})

(def expected-science-keyword-full-search-facets
  {:children [{:children [{:children [{:children [{:children [{:children [{:children [{:applied true,
                                                                                       :field :detailed-variable,
                                                                                       :type :filter,
                                                                                       :has_children false,
                                                                                       :title "Detail1",
                                                                                       :count 1,
                                                                                       :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&page_size=0&include_facets=v2"}}]
                                                                           :applied true,
                                                                           :field :variable-level-3,
                                                                           :type :filter,
                                                                           :has_children true,
                                                                           :title "Level1-3",
                                                                           :count 1,
                                                                           :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&page_size=0&include_facets=v2"}}]
                                                               :applied true,
                                                               :field :variable-level-2,
                                                               :type :filter,
                                                               :has_children true,
                                                               :title "Level1-2",
                                                               :count 1,
                                                               :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&page_size=0&include_facets=v2"}}]
                                                   :applied true,
                                                   :field :variable-level-1,
                                                   :type :filter,
                                                   :has_children true,
                                                   :title "Level1-1",
                                                   :count 1,
                                                   :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&page_size=0&include_facets=v2"}}]
                                       :applied true,
                                       :field :term,
                                       :type :filter,
                                       :has_children true,
                                       :title "Term1",
                                       :count 1,
                                       :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic1&page_size=0&include_facets=v2"}}]
                           :applied true,
                           :field :topic,
                           :type :filter,
                           :has_children true,
                           :title "Topic1",
                           :count 1,
                           :links {:remove "http://localhost:3003/collections.json?page_size=0&include_facets=v2"}}]
               :applied true,
               :field :category,
               :type :filter,
               :has_children true,
               :title "Earth Science",
               :count 1,
               :links {:apply "http://localhost:3003/collections.json?science_keywords_h%5B1%5D%5Bcategory%5D=Earth+Science&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&science_keywords_h%5B0%5D%5Bdetailed_variable%5D=Detail1&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"}}]
   :applied true,
   :type :group,
   :has_children true,
   :title "Category"})

(def science-keywords-skipped-search-aggregations
  {:science-keywords-h
   {:doc_count 1,
    :category
    {:doc_count_error_upper_bound 0,
     :sum_other_doc_count 0,
     :buckets
     [{:key "Earth Science",
       :doc_count 1,
       :topic
       {:doc_count_error_upper_bound 0,
        :sum_other_doc_count 0,
        :buckets
        [{:key "Topic4",
          :doc_count 1,
          :term
          {:doc_count_error_upper_bound 0,
           :sum_other_doc_count 0,
           :buckets
           [{:key "Term4",
             :doc_count 1,
             :variable-level-1
             {:doc_count_error_upper_bound 0,
              :sum_other_doc_count 0,
              :buckets []},
             :detailed-variable
             {:doc_count_error_upper_bound 0,
              :sum_other_doc_count 0,
              :buckets
              [{:key "Detail4",
                :doc_count 1,
                :coll-count
                {:doc_count 1,
                 :concept-id
                 {:doc_count_error_upper_bound 0,
                  :sum_other_doc_count 0,
                  :buckets
                  [{:key "C1200000008-PROV1", :doc_count 1}]}}}]},
             :coll-count
             {:doc_count 1,
              :concept-id
              {:doc_count_error_upper_bound 0,
               :sum_other_doc_count 0,
               :buckets
               [{:key "C1200000008-PROV1",
                 :doc_count 1}]}}}]},
          :coll-count
          {:doc_count 1,
           :concept-id
           {:doc_count_error_upper_bound 0,
            :sum_other_doc_count 0,
            :buckets
            [{:key "C1200000008-PROV1",
              :doc_count 1}]}}}]},
       :coll-count
       {:doc_count 1,
        :concept-id
        {:doc_count_error_upper_bound 0,
         :sum_other_doc_count 0,
         :buckets
         [{:key "C1200000008-PROV1", :doc_count 1}]}}}]}}})

(def expected-science-keyword-skipped-search-facets
 {:children [{:children [{:children [{:children [{:applied true,
                                                  :field :detailed-variable,
                                                  :type :filter,
                                                  :has_children false,
                                                  :title "Detail4",
                                                  :count 1,
                                                  :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic4&science_keywords_h%5B0%5D%5Bterm%5D=Term4&page_size=0&include_facets=v2"}}]
                                      :applied true,
                                      :field :term,
                                      :type :filter,
                                      :has_children true,
                                      :title "Term4",
                                      :count 1,
                                      :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic4&page_size=0&include_facets=v2"}}]
                          :applied true,
                          :field :topic,
                          :type :filter,
                          :has_children true,
                          :title "Topic4",
                          :count 1,
                          :links {:remove "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Bdetailed_variable%5D=Detail4&page_size=0&include_facets=v2"}}]
              :applied true,
              :field :category,
              :type :filter,
              :has_children true,
              :title "Earth Science",
              :count 1,
              :links {:apply "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Btopic%5D=Topic4&science_keywords_h%5B0%5D%5Bterm%5D=Term4&science_keywords_h%5B0%5D%5Bdetailed_variable%5D=Detail4&page_size=0&include_facets=v2&science_keywords_h%5B1%5D%5Bcategory%5D=Earth+Science"}}]
  :applied true,
  :type :group,
  :has_children true,
  :title "Category"})

(def science-keywords-skipped-with-sibling-search-aggregations
  {:doc_count 6,
   :category
   {:doc_count_error_upper_bound 0,
    :sum_other_doc_count 0,
    :buckets
    [{:key "Earth Science",
      :doc_count 3,
      :topic
      {:doc_count_error_upper_bound 0,
       :sum_other_doc_count 0,
       :buckets
       [{:key "Terrestrial Hydrosphere",
         :doc_count 2,
         :term
         {:doc_count_error_upper_bound 0,
          :sum_other_doc_count 0,
          :buckets
          [{:key "Snow/Ice",
            :doc_count 2,
            :variable-level-1
            {:doc_count_error_upper_bound 0,
             :sum_other_doc_count 0,
             :buckets
             [{:key "Snow Water Equivalent",
               :doc_count 2,
               :detailed-variable
               {:doc_count_error_upper_bound 0,
                :sum_other_doc_count 0,
                :buckets
                [{:key "Rain",
                  :doc_count 1,
                  :coll-count
                  {:doc_count 1,
                   :concept-id
                   {:doc_count_error_upper_bound 0,
                    :sum_other_doc_count 0,
                    :buckets
                    [{:key "C1200000011-PROV1",
                      :doc_count 1}]}}}]},
               :coll-count
               {:doc_count 1,
                :concept-id
                {:doc_count_error_upper_bound 0,
                 :sum_other_doc_count 0,
                 :buckets
                 [{:key "C1200000011-PROV1",
                   :doc_count 1}]}},
               :variable-level-2
               {:doc_count_error_upper_bound 0,
                :sum_other_doc_count 0,
                :buckets
                [{:key "Snow Water Equivalent",
                  :doc_count 1,
                  :detailed-variable
                  {:doc_count_error_upper_bound 0,
                   :sum_other_doc_count 0,
                   :buckets
                   []},
                  :coll-count
                  {:doc_count 1,
                   :concept-id
                   {:doc_count_error_upper_bound 0,
                    :sum_other_doc_count 0,
                    :buckets
                    [{:key "C1200000011-PROV1",
                      :doc_count 1}]}},
                  :variable-level-3
                  {:doc_count_error_upper_bound 0,
                   :sum_other_doc_count 0,
                   :buckets
                   []}}]}}]},
            :detailed-variable
            {:doc_count_error_upper_bound 0,
             :sum_other_doc_count 0,
             :buckets
             [{:key "Rain",
               :doc_count 1,
               :coll-count
               {:doc_count 1,
                :concept-id
                {:doc_count_error_upper_bound 0,
                 :sum_other_doc_count 0,
                 :buckets
                 [{:key "C1200000011-PROV1",
                   :doc_count 1}]}}}]},
            :coll-count
            {:doc_count 1,
             :concept-id
             {:doc_count_error_upper_bound 0,
              :sum_other_doc_count 0,
              :buckets
              [{:key "C1200000011-PROV1",
                :doc_count 1}]}}}]},
         :coll-count
         {:doc_count 1,
          :concept-id
          {:doc_count_error_upper_bound 0,
           :sum_other_doc_count 0,
           :buckets
           [{:key "C1200000011-PROV1",
             :doc_count 1}]}}}
        {:key "Cryosphere",
         :doc_count 1,
         :term
         {:doc_count_error_upper_bound 0,
          :sum_other_doc_count 0,
          :buckets
          [{:key "Snow/Ice",
            :doc_count 1,
            :variable-level-1
            {:doc_count_error_upper_bound 0,
             :sum_other_doc_count 0,
             :buckets
             [{:key "Snow Water Equivalent",
               :doc_count 1,
               :detailed-variable
               {:doc_count_error_upper_bound 0,
                :sum_other_doc_count 0,
                :buckets
                [{:key "Sleet",
                  :doc_count 1,
                  :coll-count
                  {:doc_count 1,
                   :concept-id
                   {:doc_count_error_upper_bound 0,
                    :sum_other_doc_count 0,
                    :buckets
                    [{:key "C1200000011-PROV1",
                      :doc_count 1}]}}}]},
               :coll-count
               {:doc_count 1,
                :concept-id
                {:doc_count_error_upper_bound 0,
                 :sum_other_doc_count 0,
                 :buckets
                 [{:key "C1200000011-PROV1",
                   :doc_count 1}]}},
               :variable-level-2
               {:doc_count_error_upper_bound 0,
                :sum_other_doc_count 0,
                :buckets
                []}}]},
            :detailed-variable
            {:doc_count_error_upper_bound 0,
             :sum_other_doc_count 0,
             :buckets
             [{:key "Sleet",
               :doc_count 1,
               :coll-count
               {:doc_count 1,
                :concept-id
                {:doc_count_error_upper_bound 0,
                 :sum_other_doc_count 0,
                 :buckets
                 [{:key "C1200000011-PROV1",
                   :doc_count 1}]}}}]},
            :coll-count
            {:doc_count 1,
             :concept-id
             {:doc_count_error_upper_bound 0,
              :sum_other_doc_count 0,
              :buckets
              [{:key "C1200000011-PROV1",
                :doc_count 1}]}}}]},
         :coll-count
         {:doc_count 1,
          :concept-id
          {:doc_count_error_upper_bound 0,
           :sum_other_doc_count 0,
           :buckets
           [{:key "C1200000011-PROV1",
             :doc_count 1}]}}}]},
      :coll-count
      {:doc_count 1,
       :concept-id
       {:doc_count_error_upper_bound 0,
        :sum_other_doc_count 0,
        :buckets
        [{:key "C1200000011-PROV1",
          :doc_count 1}]}}}
     {:key "Earth Science Services",
      :doc_count 3,
      :topic
      {:doc_count_error_upper_bound 0,
       :sum_other_doc_count 0,
       :buckets
       [{:key "Data Analysis And Visualization",
         :doc_count 3,
         :term
         {:doc_count_error_upper_bound 0,
          :sum_other_doc_count 0,
          :buckets
          [{:key "Geographic Information Systems",
            :doc_count 3,
            :variable-level-1
            {:doc_count_error_upper_bound 0,
             :sum_other_doc_count 0,
             :buckets
             []},
            :detailed-variable
            {:doc_count_error_upper_bound 0,
             :sum_other_doc_count 0,
             :buckets
             []},
            :coll-count
            {:doc_count 3,
             :concept-id
             {:doc_count_error_upper_bound 0,
              :sum_other_doc_count 2,
              :buckets
              [{:key "C1200000008-PROV1",
                :doc_count 1}]}}}]},
         :coll-count
         {:doc_count 3,
          :concept-id
          {:doc_count_error_upper_bound 0,
           :sum_other_doc_count 2,
           :buckets
           [{:key "C1200000008-PROV1",
             :doc_count 1}]}}}]},
      :coll-count
      {:doc_count 3,
       :concept-id
       {:doc_count_error_upper_bound 0,
        :sum_other_doc_count 2,
        :buckets
        [{:key "C1200000008-PROV1",
          :doc_count 1}]}}}]}})

(def expected-science-keyword-skipped-sibling-search-facets
  {:title "Category",
   :type :group,
   :applied true,
   :has_children true,
   :children
   [{:title "Earth Science",
     :type :filter,
     :applied true,
     :count 1,
     :links
     {:apply
      "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B1%5D%5Bcategory%5D=Earth+Science"},
     :has_children true,
     :children
     [{:title "Cryosphere",
       :type :filter,
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B1%5D%5Btopic%5D=Cryosphere"},
       :has_children true,
       :children
       [{:title "Snow/Ice",
         :type :filter,
         :applied false,
         :count 1,
         :links
         {:apply
          "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B1%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B1%5D%5Btopic%5D=Cryosphere"},
         :has_children true,
         :children
         [{:title "Snow Water Equivalent",
           :type :filter,
           :applied false,
           :count 1,
           :links
           {:apply
            "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent"},
           :has_children true,
           :children
           [{:title "Sleet",
             :type :filter,
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B0%5D%5Bdetailed_variable%5D=Sleet"},
             :has_children false,
             :field :detailed-variable}],
           :field :variable-level-1}],
         :field :term}],
       :field :topic}
      {:title "Terrestrial Hydrosphere",
       :type :filter,
       :applied true,
       :count 1,
       :links
       {:remove
        "http://localhost:3003/collections.json?include_facets=v2"},
       :has_children true,
       :children
       [{:title "Snow/Ice",
         :type :filter,
         :applied true,
         :count 1,
         :links
         {:remove
          "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere"},
         :has_children true,
         :children
         [{:title "Snow Water Equivalent",
           :type :filter,
           :applied true,
           :count 1,
           :links
           {:remove
            "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce"},
           :has_children true,
           :children
           [{:title "Rain",
             :type :filter,
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B0%5D%5Bdetailed_variable%5D=Rain"},
             :has_children false,
             :field :detailed-variable}
            {:title "Snow Water Equivalent",
             :type :filter,
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Snow+Water+Equivalent"},
             :has_children false,
             :field :variable-level-2}]
           :field :variable-level-1}],
         :field :term}],
       :field :topic}],
     :field :category},
    {:title "Earth Science Services",
     :type :filter,
     :applied false,
     :count 3,
     :links
     {:apply
      "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B1%5D%5Bcategory%5D=Earth+Science+Services"},
     :has_children true,
     :children
     [{:title "Data Analysis And Visualization",
       :type :filter,
       :applied false,
       :count 3,
       :links
       {:apply
        "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B1%5D%5Btopic%5D=Data+Analysis+And+Visualization"},
       :has_children true,
       :children
       [{:title "Geographic Information Systems",
         :type :filter,
         :applied false,
         :count 3,
         :links
         {:apply
          "http://localhost:3003/collections.json?include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Terrestrial+Hydrosphere&science_keywords_h%5B0%5D%5Bterm%5D=Snow%2FIce&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Snow+Water+Equivalent&science_keywords_h%5B1%5D%5Bterm%5D=Geographic+Information+Systems&science_keywords_h%5B1%5D%5Btopic%5D=Data+Analysis+And+Visualization"},
         :has_children false,
         :field :term}]
       :field :topic}]
     :field :category}]})

(deftest process-bucket-for-hierarchical-field-test
  "This function tests the processing of the facet buckets. The expectant result
  is a nested list of facets."
  (testing "Testing getting facets for the full science keyword hierarchy."
    (let [base-field :science-keywords-h
          field-hierarchy (#'hv2/nested-fields-mappings base-field)
          base-url "http://localhost:3003/collections.json"
          query-params {"science_keywords_h[0][topic]" "Topic1",
                        "science_keywords_h[0][term]" "Term1",
                        "science_keywords_h[0][variable_level_1]" "Level1-1",
                        "science_keywords_h[0][variable_level_2]" "Level1-2",
                        "science_keywords_h[0][variable_level_3]" "Level1-3",
                        "science_keywords_h[0][detailed_variable]" "Detail1",
                        "page_size" 0,
                        "include_facets" "v2"}
          elastic-aggs (base-field science-keywords-full-search-aggregations)
          facets (#'hv2/parse-hierarchical-bucket-v2 base-field field-hierarchy base-url query-params elastic-aggs)]
      (is (= expected-science-keyword-full-search-facets facets))))

  (testing "Testing getting facets for records that include a detailed-variable but missing the
            optional keywords."
    (let [base-field :science-keywords-h
          field-hierarchy (#'hv2/nested-fields-mappings base-field)
          base-url "http://localhost:3003/collections.json"
          query-params {"science_keywords_h[0][topic]" "Topic4",
                        "science_keywords_h[0][term]" "Term4",
                        "science_keywords_h[0][detailed_variable]" "Detail4",
                        "page_size" 0,
                        "include_facets" "v2"}
          elastic-aggs (base-field science-keywords-skipped-search-aggregations)
          facets (#'hv2/parse-hierarchical-bucket-v2 base-field field-hierarchy base-url query-params elastic-aggs)]
      (is (= expected-science-keyword-skipped-search-facets facets))))

  (testing "Testing getting facets for records that include a detailed-variable but missing the
            optional keywords and where the facets include a sibling facet."
    (let [base-field :science-keywords-h
          field-hierarchy (#'hv2/nested-fields-mappings base-field)
          base-url "http://localhost:3003/collections.json"
          query-params {"include_facets" "v2",
                        "science_keywords_h[0][topic]" "Terrestrial Hydrosphere",
                        "science_keywords_h[0][term]" "Snow/Ice",
                        "science_keywords_h[0][variable_level_1]" "Snow Water Equivalent"}
          elastic-aggs science-keywords-skipped-with-sibling-search-aggregations
          facets (#'hv2/parse-hierarchical-bucket-v2 base-field field-hierarchy base-url query-params elastic-aggs)]
      (is (= expected-science-keyword-skipped-sibling-search-facets facets)))))

(def v2-node
  {:title "Snow/Ice",
   :type :filter,
   :applied true,
   :count 1,
   :links {:remove "http:"},
   :has_children true,
   :children [{:title "Snow Water Equivalent",
               :type :filter,
               :applied true,
               :count 1,
               :links {:remove "http:"},
               :has_children true,
               :children [{:title "Rain",
                            :type :filter,
                            :applied false,
                            :count 1,
                            :links {:apply "http"},
                            :has_children false,
                            :field :detailed-variable}
                          {:title "Rain2",
                                       :type :filter,
                                       :applied false,
                                       :count 1,
                                       :links {:apply "http"},
                                       :has_children false,
                                       :field :detailed-variable}
                          {:title "Snow Water Equivalent",
                           :type :filter,
                           :applied false,
                           :count 1,
                           :links {:apply "http:"},
                           :has_children true,
                           :children [{:title "Snow Water Equivalent",
                                        :type :filter,
                                        :applied false,
                                        :count 1,
                                        :links {:apply "http"},
                                        :has_children false,
                                        :field :detailed-variable}]
                           :field :variable-level-2}]
               :field :variable-level-1}]
   :field :term})

(deftest last-facet-accounted-for?-test
  "This function tests the last-facet-accounted-for? function where it works its way
  through the sub-facets and sees if the science-keyword detailed-variable field exists. Returns
  true if it does and nil if it doesn't."
  (is (= true (#'hv2/last-facet-accounted-for? v2-node :detailed-variable "Rain"))))

(deftest filter-out-accounted-for-facets-test
  "Tests the filter-out-accounted-for-facets funtion. The function iterates over a list of sibling
  facets and checks to see if they have already been accounted for. If they have not then the left
  over facets are added into the current facet node."

  (are3 [expected subfield new-facets]
    (is (= expected (seq (#'hv2/filter-out-accounted-for-facets v2-node subfield new-facets))))

    "Test if there are no new sibling sub-facets."
    nil :detailed-variable {}

    "Test if the new sub-facets are not included in the v2-node."
    [{:title "Mud",
      :type :filter,
      :applied false,
      :count 1,
      :links {:apply "http:"},
      :has_children false,
      :field :detailed-variable}
     {:title "Dirt",
      :type :filter,
      :applied false,
      :count 1,
      :links {:apply "http:"},
      :has_children false,
      :field :detailed-variable}]
    :detailed-variable
    {:title "Detailed_variable",
       :type :group,
       :applied true,
       :has_children true,
       :children [{:title "Mud",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}
                  {:title "Dirt",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}]}

    "Test if the 1 of the new sub-facets are not included in the v2-node, but the other is."
    [{:title "Mud",
      :type :filter,
      :applied false,
      :count 1,
      :links {:apply "http:"},
      :has_children false,
      :field :detailed-variable}]
    :detailed-variable
    {:title "Detailed_variable",
       :type :group,
       :applied true,
       :has_children true,
       :children [{:title "Mud",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}
                  {:title "Rain",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}]}

    "Test if 2 of the new sub-facets are included in the v2-node in different parts of the facet structure."
    nil
    :detailed-variable
    {:title "Detailed_variable",
       :type :group,
       :applied true,
       :has_children true,
       :children [{:title "Snow Water Equivalent",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}
                  {:title "Rain",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}]}

    "Test if the all of the new sub-facets are included in the v2-node."
    nil
    :detailed-variable
    {:title "Detailed_variable",
       :type :group,
       :applied true,
       :has_children true,
       :children [{:title "Snow Water Equivalent",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}
                  {:title "Rain",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http:"},
                   :has_children false,
                   :field :detailed-variable}
                  {:title "Rain2",
                   :type :filter,
                   :applied false,
                   :count 1,
                   :links {:apply "http"},
                   :has_children false,
                   :field :detailed-variable}]}))
