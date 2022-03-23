(ns cmr.search.test.services.query-execution.facets.facets-v2-results-feature-test
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.search.services.humanizers.humanizer-range-facet-service :as rfs]
   [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
   [cmr.search.services.query-execution.facets.collection-v2-facets :as cv2f]))

(def expected-pre-process-query-result-feature-result
  {:project-h
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
           {:terms {:field :concept-id, :size 1}}}},
         :term
         {:terms
          {:field "science-keywords-humanized.term", :size 50},
          :aggs
          {:coll-count
           {:reverse_nested {},
            :aggs
            {:concept-id
             {:terms {:field :concept-id, :size 1}}}},
           :detailed-variable
           {:terms
            {:field
             "science-keywords-humanized.detailed-variable",
             :size 50},
            :aggs
            {:coll-count
             {:reverse_nested {},
              :aggs
              {:concept-id
               {:terms
                {:field :concept-id, :size 1}}}}}}}}}}}}}},
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
   :latency-h {:terms {:field :latency, :size 50}}
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
        {:field :granule-data-format-humanized.priority}}}}}},
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
   :horizontal-data-resolution-range
   {:nested {:path :horizontal-data-resolutions},
    :aggs
    {:values
     {:range
      {:field :horizontal-data-resolutions.value,
       :ranges
       [{:key "0 to 1 meter", :from 0.0, :to 1.0001}
        {:key "1 to 30 meters", :from 1.0, :to 30.0001}
        {:key "30 to 100 meters",
         :from 30.0,
         :to 100.0001}
        {:key "100 to 250 meters",
         :from 100.0,
         :to 250.0001}
        {:key "250 to 500 meters",
         :from 250.0,
         :to 500.0001}
        {:key "500 to 1000 meters",
         :from 500.0,
         :to 1000.0001}
        {:key "1 to 10 km", :from 1000.0, :to 10000.0001}
        {:key "10 to 50 km",
         :from 10000.0,
         :to 50000.0001}
        {:key "50 to 100 km",
         :from 50000.0,
         :to 100000.0001}
        {:key "100 to 250 km",
         :from 100000.0,
         :to 250000.0001}
        {:key "250 to 500 km",
         :from 250000.0,
         :to 500000.0001}
        {:key "500 to 1000 km",
         :from 500000.0,
         :to 1000000.0001}
        {:key "1000 km & beyond",
         :from 1000000.0,
         :to 3.4028234663852886E38}]},
      :aggs
      {:priority
       {:avg
        {:field :horizontal-data-resolutions.priority}}}}}},
   :platforms-h
   {:nested {:path :platforms2-humanized},
    :aggs
    {:basis
     {:terms {:field "platforms2-humanized.basis", :size 50},
      :aggs
      {:coll-count
       {:reverse_nested {},
        :aggs
        {:concept-id {:terms {:field :concept-id, :size 1}}}},
       :category
       {:terms
        {:field "platforms2-humanized.category", :size 50},
        :aggs
        {:coll-count
         {:reverse_nested {},
          :aggs
          {:concept-id
           {:terms {:field :concept-id, :size 1}}}},
         :short-name
         {:terms
          {:field "platforms2-humanized.short-name", :size 50},
          :aggs
          {:coll-count
           {:reverse_nested {},
            :aggs
            {:concept-id
             {:terms {:field :concept-id, :size 1}}}}}},
         :sub-category
         {:terms
          {:field "platforms2-humanized.sub-category",
           :size 50},
          :aggs
          {:coll-count
           {:reverse_nested {},
            :aggs
            {:concept-id
             {:terms {:field :concept-id, :size 1}}}}}}}}}}}},
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
           {:terms {:field :concept-id, :size 1}}}}}}}}}}})

(deftest pre-process-query-result-feature-test
  (testing "Testing the preprocessing of the query without facets in the query."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true"}
          query {:concept-type :collection
                 :facet-fields nil
                 :facets-size nil}]
      (is (= expected-pre-process-query-result-feature-result
             (:aggregations (query-execution/pre-process-query-result-feature context query :facets-v2))))))

  (testing "Test query includes a facet."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true&two_d_coordinate_system_name%5B%5D=MODIS+Tile+EASE"}
          query {:concept-type :collection
                 :facet-fields [:two-d-coordinate-system-name]
                 :facets-size nil}]
      (is (= {:two-d-coordinate-system-name-h
               {:terms {:field :two-d-coord-name, :size 50}}}
             (:aggregations (query-execution/pre-process-query-result-feature context query :facets-v2))))))

  (testing "Test query includes a horizontal-data-resolutions facet."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true&horizontal-data-resolution-range%5B%5D=%5B%5D=1%20to%2030%20meters"}
          query {:concept-type :collection
                 :facet-fields [:horizontal-data-resolution-range]}]
      (is (= {:horizontal-data-resolution-range
              {:nested {:path :horizontal-data-resolutions},
               :aggs
               {:values
                {:range
                 {:field :horizontal-data-resolutions.value,
                  :ranges
                  [{:key "0 to 1 meter", :from 0.0, :to (+ 1.0 rfs/addition-factor)}
                   {:key "1 to 30 meters", :from 1.0, :to (+ 30.0 rfs/addition-factor)}
                   {:key "30 to 100 meters", :from 30.0, :to (+ 100.0 rfs/addition-factor)}
                   {:key "100 to 250 meters", :from 100.0, :to (+ 250.0 rfs/addition-factor)}
                   {:key "250 to 500 meters", :from 250.0, :to (+ 500.0 rfs/addition-factor)}
                   {:key "500 to 1000 meters", :from 500.0, :to (+ 1000.0 rfs/addition-factor)}
                   {:key "1 to 10 km", :from 1000.0, :to (+ 10000.0 rfs/addition-factor)}
                   {:key "10 to 50 km", :from 10000.0, :to (+ 50000.0 rfs/addition-factor)}
                   {:key "50 to 100 km", :from 50000.0, :to (+ 100000.0 rfs/addition-factor)}
                   {:key "100 to 250 km", :from 100000.0, :to (+ 250000.0 rfs/addition-factor)}
                   {:key "250 to 500 km", :from 250000.0, :to (+ 500000.0 rfs/addition-factor)}
                   {:key "500 to 1000 km", :from 500000.0, :to (+ 1000000.0 rfs/addition-factor)}
                   {:key "1000 km & beyond", :from 1000000.0, :to (Float/MAX_VALUE)}]},
                 :aggs
                 {:priority
                  {:avg
                   {:field :horizontal-data-resolutions.priority}}}}}}}
             (:aggregations (query-execution/pre-process-query-result-feature context query :facets-v2)))))))

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
             (:facets (query-execution/post-process-query-result-feature context query elastic-results query-results :facets-v2))))))

  (testing "Testing the post processing of the query without facets in the query for
            horizontal data resolutions."
    (let [context {:query-string "keyword=*&include_facets=v2&pretty=true"
                   :system {:public-conf {:protocol "http",
                                          :port 3003,
                                          :host "localhost",
                                          :relative-root-url ""}}}
          query {:concept-type :collection
                 :facet-fields nil}
          elastic-results {:aggregations
                            {:horizontal-data-resolution-range {:doc_count 1,
                                                                :values {:buckets [{:key "0 to 1 meter",
                                                                                    :from 0.0,
                                                                                    :to 1.0,
                                                                                    :doc_count 1,
                                                                                    :priority {:value 0.0}}
                                                                                   {:key "1 to 30 meters",
                                                                                    :from 1.0,
                                                                                    :to 30.0,
                                                                                    :doc_count 1,
                                                                                    :priority {:value 0.0}}
                                                                                   {:key "30 to 100 meters",
                                                                                    :from 30.0,
                                                                                    :to 100.0,
                                                                                    :doc_count 0,
                                                                                    :priority {:value nil}}
                                                                                   {:key "100 to 250 meters",
                                                                                    :from 100.0,
                                                                                    :to 250.0,
                                                                                    :doc_count 0,
                                                                                    :priority {:value nil}}
                                                                                   {:key "250 to 500 meters",
                                                                                    :from 250.0,
                                                                                    :to 500.0,
                                                                                    :doc_count 0,
                                                                                    :priority {:value nil}}
                                                                                   {:key "500 to 1000 meters",
                                                                                    :from 500.0,
                                                                                    :to 1000.0,
                                                                                    :doc_count 0,
                                                                                    :priority {:value nil}}
                                                                                   {:key "1 to 10 km",
                                                                                    :from 1000.0,
                                                                                    :to 10000.0,
                                                                                    :doc_count 1,
                                                                                    :priority {:value 0.0}}
                                                                                   {:key "10 to 50 km",
                                                                                    :from 10000.0,
                                                                                    :to 50000.0,
                                                                                    :doc_count 1,
                                                                                    :priority {:value 0.0}}
                                                                                   {:key "50 to 100 km",
                                                                                    :from 50000.0,
                                                                                    :to 100000.0,
                                                                                    :doc_count 1,
                                                                                    :priority {:value 0.0}}
                                                                                   {:key "100 to 250 km",
                                                                                    :from 100000.0,
                                                                                    :to 250000.0,
                                                                                    :doc_count 1,
                                                                                    :priority {:value 0.0}}
                                                                                   {:key "250 to 500 km",
                                                                                    :from 250000.0,
                                                                                    :to 500000.0,
                                                                                    :doc_count 0,
                                                                                    :priority {:value nil}}
                                                                                   {:key "500 to 1000 km",
                                                                                    :from 500000.0,
                                                                                    :to 1000000.0,
                                                                                    :doc_count 0,
                                                                                    :priority {:value nil}}
                                                                                   {:key "1000 km & beyond",
                                                                                    :from 1000000.0,
                                                                                    :to (Float/MAX_VALUE),
                                                                                    :doc_count 0,
                                                                                    :priority {:value nil}}]}}}}
          query-results nil]
      (is (= {:title "Browse Collections",
              :type :group,
              :has_children true,
              :children
              [{:title "Horizontal Data Resolution",
                :type :group,
                :applied false,
                :has_children true,
                :children
                [{:title "0 to 1 meter",
                  :type :filter,
                  :applied false,
                  :count 1,
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=0+to+1+meter"},
                  :has_children false}
                 {:title "1 to 30 meters",
                  :type :filter,
                  :applied false,
                  :count 1,
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=1+to+30+meters"},
                  :has_children false}
                 {:title "1 to 10 km",
                  :type :filter,
                  :applied false,
                  :count 1,
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=1+to+10+km"},
                  :has_children false}
                 {:title "10 to 50 km",
                  :type :filter,
                  :applied false,
                  :count 1,
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=10+to+50+km"},
                  :has_children false}
                 {:title "50 to 100 km",
                  :type :filter,
                  :applied false,
                  :count 1,
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=50+to+100+km"},
                  :has_children false}
                 {:title "100 to 250 km",
                  :type :filter,
                  :applied false,
                  :count 1,
                  :links
                  {:apply
                   "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=100+to+250+km"},
                  :has_children false}]}]}
             (:facets (query-execution/post-process-query-result-feature context query elastic-results query-results :facets-v2))))))

  (testing "Testing the post processing of the query with facets in the query for
            horizontal data resolutions."
    (let [context {:query-string
                    "keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=0+to+1+meter"
                   :system {:public-conf {:protocol "http",
                                          :port 3003,
                                          :host "localhost",
                                          :relative-root-url ""}}}
          query {:concept-type :collection
                 :condition {:operation :and
                             :conditions [{:field :keyword
                                           :query-str *}
                                          {:path :horizontal-data-resolutions
                                           :condition {:field :horizontal-data-resolutions.value
                                                       :min-value 0.0
                                                       :max-value 1.0
                                                       :exclusive? false}}]}}
          facet-fields [:horizontal-data-resolutions]
          elastic-results {:aggregations
                            {:horizontal-data-resolution-range
                              {:doc_count 1
                               :values {:buckets [{:key "0 to 1 meter"
                                                   :from 0.0
                                                   :to 1.0
                                                   :doc_count 1
                                                   :priority {:value 0.0}}
                                                  {:key "1 to 30 meters"
                                                   :from 1.0
                                                   :to 30.0
                                                   :doc_count 1
                                                   :priority {:value 0.0}}
                                                  {:key "30 to 100 meters"
                                                   :from 30.0
                                                   :to 100.0
                                                   :doc_count 0
                                                   :priority {:value nil}}
                                                  {:key "1 to 10 km"
                                                   :from 1000.0
                                                   :to 10000.0
                                                   :doc_count 1
                                                   :priority {:value 0.0}}
                                                  {:key "1000 km & beyond"
                                                   :from 1000000.0
                                                   :to (Float/MAX_VALUE)
                                                   :doc_count 0
                                                   :priority {:value nil}}]}}}}

          query-results nil]
      (is (= {:title "Browse Collections",
                 :type :group,
                 :has_children true,
                 :children
                 [{:title "Horizontal Data Resolution",
                   :type :group,
                   :applied true,
                   :has_children true,
                   :children
                   [{:title "0 to 1 meter",
                     :type :filter,
                     :applied true,
                     :count 1,
                     :links
                     {:remove
                      "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true"},
                     :has_children false}
                    {:title "1 to 30 meters",
                     :type :filter,
                     :applied false,
                     :count 1,
                     :links
                     {:apply
                      "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=1+to+30+meters&horizontal_data_resolution_range%5B%5D=0+to+1+meter"},
                     :has_children false}
                    {:title "1 to 10 km",
                     :type :filter,
                     :applied false,
                     :count 1,
                     :links
                     {:apply
                      "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&horizontal_data_resolution_range%5B%5D=1+to+10+km&horizontal_data_resolution_range%5B%5D=0+to+1+meter"},
                     :has_children false}]}]}
             (:facets (query-execution/post-process-query-result-feature
                        context query elastic-results query-results :facets-v2)))))))
