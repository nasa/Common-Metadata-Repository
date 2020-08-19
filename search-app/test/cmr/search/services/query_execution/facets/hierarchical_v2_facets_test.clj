(ns cmr.search.services.query-execution.facets.hierarchical-v2-facets-test
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.hierarchical-v2-facets :as hv2]))

(deftest create-hierarchical-v2-facets-test
  (is (not= nil (hv2/create-hierarchical-v2-facets 
                  {:cycle
                   {:buckets
                    [{:key 3.0
                      :doc_count 6
                      :buckets
                      {:doc_count 11
                       :pass
                       {:buckets
                        [{:key 1.0 :doc_count 2}
                         {:key 2.0 :doc_count 1}
                         {:key 3.0 :doc_count 4}
                         {:key 4.0 :doc_count 4}]}}}
                     {:key 42.0
                      :doc_count 2
                      :buckets
                      {:doc_count 3
                       :pass
                       {:buckets
                        [{:key 1.0 :doc_count 1}
                         {:key 3.0 :doc_count 1}
                         {:key 4.0 :doc_count 1}]}}}]}}
                  "localhost:3000/granules.json"
                  {"page_size" "10"}
                  :cycle))))

(deftest hierarchical-bucket-map->facets-v2-test
  (testing "cycle"
    (let [result (hv2/hierarchical-bucket-map->facets-v2
                   :cycle
                   {:buckets
                    [{:key 3.0
                      :doc_count 6
                      :passes
                      {:doc_count 11
                       :pass
                       {:buckets
                        [{:key 1.0 :doc_count 2}
                         {:key 2.0 :doc_count 1}
                         {:key 3.0 :doc_count 4}
                         {:key 4.0 :doc_count 4}]}}}
                     {:key 42.0
                      :doc_count 2
                      :passes
                      {:doc_count 3
                       :pass
                       {:buckets
                        [{:key 1.0 :doc_count 1}
                         {:key 3.0 :doc_count 1}
                         {:key 4.0 :doc_count 1}]}}}]}
                   "http://localhost:3003/granules.json"
                   {"page_size" "0"
                    "include_facets" "v2"})]
      (is (= {:title "Cycle"          
              :type :group
              :applied true
              :has_children true
              :children
              [{:title "3"
                :type :filter
                :applied false
                :count 6
                :links
                {:apply
                 "http://localhost:3003/granules.json?page_size=0&include_facets=v2&cycle%5B%5D=3"}
                :has_children true}
               {:title "42"
                :type :filter
                :applied false
                :count 2
                :links
                {:apply
                 "http://localhost:3003/granules.json?page_size=0&include_facets=v2&cycle%5B%5D=3"}
                :has_children true}]}
             result))))

  (testing "science-keywords-h"
    (let [result (hv2/hierarchical-bucket-map->facets-v2 
                   :science-keywords-h
                   {:doc_count 2
                    :category
                    {:doc_count_error_upper_bound 0
                     :sum_other_doc_count 0
                     :buckets
                     [{:key "Earth Science"
                       :doc_count 2
                       :topic
                       {:doc_count_error_upper_bound 0
                        :sum_other_doc_count 0
                        :buckets
                        [{:key "Topic1"
                          :doc_count 1
                          :term
                          {:doc_count_error_upper_bound 0
                           :sum_other_doc_count 0
                           :buckets
                           [{:key "Term3"
                             :doc_count 1
                             :variable-level-1
                             {:doc_count_error_upper_bound 0
                              :sum_other_doc_count 0
                              :buckets []}
                             :coll-count
                             {:doc_count 1
                              :concept-id
                              {:doc_count_error_upper_bound 0
                               :sum_other_doc_count 0
                               :buckets [{:key "C1200000010-PROV1" :doc_count 1}]}}}]}
                          :coll-count
                          {:doc_count 1
                           :concept-id
                           {:doc_count_error_upper_bound 0
                            :sum_other_doc_count 0
                            :buckets [{:key "C1200000010-PROV1" :doc_count 1}]}}}
                         {:key "Topic2"
                          :doc_count 1
                          :term
                          {:doc_count_error_upper_bound 0
                           :sum_other_doc_count 0
                           :buckets
                           [{:key "Term3"
                             :doc_count 1
                             :variable-level-1
                             {:doc_count_error_upper_bound 0
                              :sum_other_doc_count 0
                              :buckets []}
                             :coll-count
                             {:doc_count 1
                              :concept-id
                              {:doc_count_error_upper_bound 0
                               :sum_other_doc_count 0
                               :buckets [{:key "C1200000010-PROV1" :doc_count 1}]}}}]}
                          :coll-count
                          {:doc_count 1
                           :concept-id
                           {:doc_count_error_upper_bound 0
                            :sum_other_doc_count 0
                            :buckets [{:key "C1200000010-PROV1" :doc_count 1}]}}}]}
                       :coll-count
                       {:doc_count 1
                        :concept-id
                        {:doc_count_error_upper_bound 0
                         :sum_other_doc_count 0
                         :buckets [{:key "C1200000010-PROV1" :doc_count 1}]}}}]}}
                   "http://localhost:3003/collections.json"
                   {"platform_h" "P3"
                    "science_keywords_h[0][topic]" "Topic1"
                    "page_size" "0"
                    "include_facets" "v2"})]
      (is (= {:title "Category"
              :type :group
              :applied true
              :has_children true
              :children
              [{:title "Topic1"
                :type :filter
                :applied true
                :count 1
                :links
                {:remove
                 "http://localhost:3003/collections.json?platform_h=P3&page_size=0&include_facets=v2"}
                :has_children true
                :children
                [{:title "Term3"
                  :type :filter
                  :applied false
                  :count 1
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?platform_h=P3&science_keywords_h%5B0%5D%5Btopic%5D=Topic1&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Bterm%5D=Term3"}
                  :has_children false}]}
               {:title "Topic2"
                :type :filter
                :applied false
                :count 1
                :links
                {:apply
                 "http://localhost:3003/collections.json?platform_h=P3&science_keywords_h%5B0%5D%5Btopic%5D=Topic1&page_size=0&include_facets=v2&science_keywords_h%5B1%5D%5Btopic%5D=Topic2"}
                :has_children true}]}
             result)))))
