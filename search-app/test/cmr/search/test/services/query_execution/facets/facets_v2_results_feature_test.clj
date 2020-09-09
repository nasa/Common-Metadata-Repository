(ns cmr.search.test.services.query-execution.facets.facets-v2-results-feature-test
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
   [cmr.search.services.query-execution.facets.collection-v2-facets :as cv2f]))

(deftest pre-process-query-result-feature-test
  (testing "Testing the preprocessing of the query without facets in the query."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true"}
          query {:concept-type :collection
                 :facet-fields nil
                 :facets-size nil}]
      (is (= {:project-h
               {:nested {:path :project-sn-humanized},
                :aggs
                {:values
                 {:terms
                  {:field :project-sn-humanized.value,
                   :size 50,
                   :order [{:priority :desc} {:_count :desc}]},
                  :aggs
                  {:priority
                   {:avg {:field :project-sn-humanized.priority}}}}}},
              :science-keywords-h
               {:nested {:path :science-keywords-humanized},
                :aggs
                {:category
                 {:terms
                  {:field "science-keywords-humanized.category", :size 50},
                  :aggs
                  {:coll-count
                   {:reverse_nested {},
                    :aggs
                    {:concept-id {:terms {:field :concept-id, :size 1}}}},
                   :topic
                   {:terms
                    {:field "science-keywords-humanized.topic", :size 50},
                    :aggs
                    {:coll-count
                     {:reverse_nested {},
                      :aggs
                       {:concept-id
                        {:terms {:field :concept-id, :size 1}}}}
                     :term
                     {:terms
                      {:field "science-keywords-humanized.term", :size 50},
                      :aggs
                      {:coll-count
                       {:reverse_nested {},
                        :aggs
                        {:concept-id
                         {:terms {:field :concept-id, :size 1}}}}}}}}}}}}
              :data-center-h
               {:nested {:path :organization-humanized},
                :aggs
                {:values
                 {:terms
                  {:field :organization-humanized.value,
                   :size 50,
                   :order [{:priority :desc} {:_count :desc}]},
                  :aggs
                  {:priority
                   {:avg {:field :organization-humanized.priority}}}}}},
              :processing-level-id-h
               {:nested {:path :processing-level-id-humanized},
                :aggs
                {:values
                 {:terms
                  {:field :processing-level-id-humanized.value,
                   :size 50,
                   :order [{:priority :desc} {:_count :desc}]},
                  :aggs
                  {:priority
                   {:avg
                    {:field :processing-level-id-humanized.priority}}}}}},
              :platform-h
               {:nested {:path :platform-sn-humanized},
                :aggs
                {:values
                 {:terms
                  {:field :platform-sn-humanized.value,
                   :size 50,
                   :order [{:priority :desc} {:_count :desc}]},
                  :aggs
                  {:priority
                   {:avg {:field :platform-sn-humanized.priority}}}}}},
              :granule-data-format-h
               {:nested {:path :granule-data-format-humanized},
                :aggs
                {:values
                 {:terms
                  {:field :granule-data-format-humanized.value,
                   :size 50,
                   :order [{:priority :desc} {:_count :desc}]},
                  :aggs
                  {:priority
                   {:avg
                    {:field :granule-data-format-humanized.priority}}}}}}
              :instrument-h
               {:nested {:path :instrument-sn-humanized},
                :aggs
                {:values
                 {:terms
                  {:field :instrument-sn-humanized.value,
                   :size 50,
                   :order [{:priority :desc} {:_count :desc}]},
                  :aggs
                  {:priority
                   {:avg {:field :instrument-sn-humanized.priority}}}}}},
              :two-d-coordinate-system-name-h
               {:terms {:field :two-d-coord-name, :size 50}},
              :variables-h
               {:nested {:path :variables},
                :aggs
                {:measurement
                 {:terms {:field "variables.measurement", :size 50},
                  :aggs
                  {:coll-count
                   {:reverse_nested {},
                    :aggs
                    {:concept-id {:terms {:field :concept-id, :size 1}}}},
                   :variable
                   {:terms {:field "variables.variable", :size 50},
                    :aggs
                    {:coll-count
                     {:reverse_nested {},
                      :aggs
                      {:concept-id
                       {:terms {:field :concept-id, :size 1}}}}}}}}}}}
             (:aggregations (query-execution/pre-process-query-result-feature context query :facets-v2))))))

  (testing "Test query includes a facet."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true&two_d_coordinate_system_name%5B%5D=MODIS+Tile+EASE"}
          query {:concept-type :collection
                 :facet-fields [:two-d-coordinate-system-name]
                 :facets-size nil}]
      (is (= {:two-d-coordinate-system-name-h
               {:terms {:field :two-d-coord-name, :size 50}}}
             (:aggregations (cmr.common-app.services.search.query-execution/pre-process-query-result-feature context query :facets-v2)))))))

(deftest post-process-query-result-feature-test
  (testing "Testing the post processing of the query without facets in the query."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true"
                   :system {:public-conf {:protocol "http",
                                          :port 3003,
                                          :host "localhost",
                                          :relative-root-url ""}}}
          query {:concept-type :collection
                 :facet-fields nil}
          elastic-results {:aggregations
                            {:two-d-coordinate-system-name-h {:doc_count_error_upper_bound 0,
                                                              :buckets [{:key "MODIS Tile EASE",
                                                                         :doc_count 1,}]
                                                              :sum_other_doc_count 0}}}
          query-results nil]
      (is (= {:title "Browse Collections",
              :type :group,
              :has_children true,
              :children
              [{:title "Tiling System",
                :type :group,
                :applied false,
                :has_children true,
                :children
                [{:title "MODIS Tile EASE",
                  :type :filter,
                  :applied false,
                  :count 1,
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&two_d_coordinate_system_name%5B%5D=MODIS+Tile+EASE"},
                  :has_children false}]}]}
             (:facets (query-execution/post-process-query-result-feature context query elastic-results query-results :facets-v2))))))

  (testing "Testing the post processing of the query with facets in the query."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true&two_d_coordinate_system_name%5B%5D=MODIS+Tile+EASE"
                   :system {:public-conf {:protocol "http",
                                          :port 3003,
                                          :host "localhost",
                                          :relative-root-url ""}}}
          query {:concept-type :collection
                 :facet-fields [:two-d-coordinate-system-name]}
          elastic-results {:aggregations
                            {:two-d-coordinate-system-name-h {:doc_count_error_upper_bound 0,
                                                              :buckets [{:key "MODIS Tile EASE",
                                                                         :doc_count 1,}]
                                                              :sum_other_doc_count 0}}}
          query-results nil]
      (is (= {:title "Browse Collections",
                 :type :group,
                 :has_children true,
                 :children
                 [{:title "Tiling System",
                   :type :group,
                   :applied true,
                   :has_children true,
                   :children
                   [{:title "MODIS Tile EASE",
                     :type :filter,
                     :applied true,
                     :count 1,
                     :links
                     {:remove
                      "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true"},
                     :has_children false}]}]}
             (:facets (query-execution/post-process-query-result-feature context query elastic-results query-results :facets-v2)))))))
