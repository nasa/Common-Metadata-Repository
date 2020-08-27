(ns cmr.search.services.query-execution.facets.granule-v2-facets-test
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
            [cmr.search.services.query-execution.facets.granule-v2-facets :as gv2f]))

(deftest create-cycle-subfacets-map-test
  (testing "no filter added"
    (let [result (gv2f/create-spatial-subfacets-map
                   "http://localhost:3003/granules.json"
                   {"page_size" "10"
                    "include_facets" "v2"
                    "collection_concept_id" "C1-PROV1"}
                   {:cycle {:buckets [{:key 3.0 :doc_count 6}
                                      {:key 42.0 :doc_count 2}]}})
          cycle-facet (first (:children result))]
      (is (= "Spatial" (:title result)))
      (is (= 1 (count (:children result))))

      (is (= "Cycle" (:title cycle-facet)))
      (is (= {:apply "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&cycle%5B%5D=3"}
             (:links (first (:children cycle-facet)))))

      (is (= "42" (:title (second (:children cycle-facet)))))
      (is (= {:apply "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&cycle%5B%5D=42"}
             (:links (second (:children cycle-facet)))))))

  (testing "cycle selected"
    (let [result (gv2f/create-spatial-subfacets-map
                   "http://localhost:3003/granules.json"
                   {"page_size" "10"
                    "include_facets" "v2"
                    "collection_concept_id" "C1-PROV1"
                    "cycle[]" "42"}
                   {:start-date-doc-values {:buckets []}
                    :cycle
                    {:doc_count 3
                     :pass
                     {:buckets
                      [{:key 1.0 :doc_count 1}
                       {:key 3.0 :doc_count 1}
                       {:key 4.0 :doc_count 1}]}}})
          cycle-42 (-> result :children first :children first)]
      (is (= "42" (:title cycle-42)))
      (is (= {:remove
              "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1"}
             (:links cycle-42))))))

(deftest create-v2-facets-by-concept-type-test
  (testing "temporal"
    (let [result (v2-facets/create-v2-facets-by-concept-type
                   :granule
                   "http://localhost:3003/granules.json"
                   {"page_size" "10"
                    "include_facets" "v2"
                    "collection_concept_id" "C1-PROV1"}
                   {:start-date-doc-values
                    {:buckets
                     [{:key_as_string "1999-01-01T00:00:00.000Z"
                       :key 915148800000
                       :doc_count 1}
                      {:key_as_string "2010-01-01T00:00:00.000Z"
                       :key 1262304000000
                       :doc_count 3}
                      {:key_as_string "2011-01-01T00:00:00.000Z"
                       :key 1293840000000
                       :doc_count 1}
                      {:key_as_string "2012-01-01T00:00:00.000Z"
                       :key 1325376000000
                       :doc_count 1}]}}
                   nil)]
      (is (= [{:title "Temporal"
               :type :group
               :applied false
               :has_children true
               :children
               [{:title "Year"
                 :type :group
                 :applied false
                 :has_children true
                 :children
                 '({:title "1999"
                    :type :filter
                    :applied false
                    :count 1
                    :links
                    {:apply
                     "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&temporal_facet%5B0%5D%5Byear%5D=1999"}
                    :has_children true}
                   {:title "2010"
                    :type :filter
                    :applied false
                    :count 3
                    :links
                    {:apply
                     "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&temporal_facet%5B0%5D%5Byear%5D=2010"}
                    :has_children true}
                   {:title "2011"
                    :type :filter
                    :applied false
                    :count 1
                    :links
                    {:apply
                     "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&temporal_facet%5B0%5D%5Byear%5D=2011"}
                    :has_children true}
                   {:title "2012"
                    :type :filter
                    :applied false
                    :count 1
                    :links
                    {:apply
                     "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&temporal_facet%5B0%5D%5Byear%5D=2012"}
                    :has_children true})}]}]
             result))))

  (testing "Spatial"
    (let [result (v2-facets/create-v2-facets-by-concept-type
                   :granule
                   "http://localhost:3003/granules.json"
                   {"page_size" "10"
                    "include_facets" "v2"
                    "collection_concept_id" "C1-PROV1"}
                   {:cycle {:buckets [{:key 3.0 :doc_count 6}
                                      {:key 42.0 :doc_count 2}]}}
                   nil)]
      (is (= "Spatial" (:title (first result))))
      (is (= 1 (count (:children (first result)))))
      (is (= "Cycle" (:title (first (:children (first result))))))))
  (testing "cycle and pass"
    (let [result (v2-facets/create-v2-facets-by-concept-type
                   :granule
                   "http://localhost:3003/granules.json"
                   {"page_size" "10"
                    "include_facets" "v2"
                    "collection_concept_id" "C1-PROV1"}
                   {:start-date-doc-values {:buckets []}
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
                          {:key 4.0 :doc_count 1}]}}}]}}
                   nil)]
      (is (= [{:title "Spatial"
               :type :group
               :applied false
               :has_children true
               :children
               [{:title "Cycle"
                 :type :group
                 :applied false
                 :has_children true
                 :children
                 [{:title "3"
                   :type :filter
                   :applied false
                   :count 6
                   :links
                   {:apply
                    "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&cycle%5B%5D=3"}
                   :has_children false}
                  {:title "42"
                   :type :filter
                   :applied false
                   :count 2
                   :links
                   {:apply
                    "http://localhost:3003/granules.json?page_size=10&include_facets=v2&collection_concept_id=C1-PROV1&cycle%5B%5D=42"}
                   :has_children false}]}]}]
             result)))))
