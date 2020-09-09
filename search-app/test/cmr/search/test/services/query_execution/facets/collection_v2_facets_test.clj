(ns cmr.search.test.services.query-execution.facets.collection-v2-facets-test
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.facets-v2-results-feature :as v2-facets]
            [cmr.search.services.query-execution.facets.collection-v2-facets :as cv2f]))

(deftest create-v2-facets-by-concept-type-test
  (testing "Testing that facets are applied and that the link shows the how to remove them."
    (let [base-url "http://localhost:3003/collections.json"
          query-params {"keyword" "*", "include_facets" "v2", "pretty" true, "two_d_coordinate_system_name[]" "MODIS Tile EASE"}
          aggs {:two-d-coordinate-system-name-h 
                 {:doc_count_error_upper_bound 0,
                  :sum_other_doc_count 0,
                  :buckets [{:key "MODIS Tile EASE", :doc_count 1}]}}
          facet-fields [:two-d-coordinate-system-name]]
      (is (= (v2-facets/create-v2-facets-by-concept-type
               :collection base-url query-params aggs facet-fields)
             '({:children ({:applied true,
                            :type :filter,
                            :has_children false,
                            :title "MODIS Tile EASE",
                            :count 1,
                            :links {:remove "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true"}})
                :applied true,
                :type :group,
                :has_children true,
                :title "Tiling System"})))))


  (testing "Testing that facets are not applied and that the link shows the how to apply them."
    (let [base-url "http://localhost:3003/collections.json"
          query-params {"keyword" "*", "include_facets" "v2", "pretty" true}
          aggs {:two-d-coordinate-system-name-h
                 {:doc_count_error_upper_bound 0,
                  :sum_other_doc_count 0,
                  :buckets [{:key "MODIS Tile EASE", :doc_count 1}]}}
          facet-fields '(:two-d-coordinate-system-name)]
      (is (= (v2-facets/create-v2-facets-by-concept-type
               :collection base-url query-params aggs facet-fields)
             '({:children ({:applied false,
                            :type :filter,
                            :has_children false,
                            :title "MODIS Tile EASE",
                            :count 1,
                            :links {:apply "http://localhost:3003/collections.json?keyword=*&include_facets=v2&pretty=true&two_d_coordinate_system_name%5B%5D=MODIS+Tile+EASE"}})
                :applied false,
                :type :group,
                :has_children true,
                :title "Tiling System"}))))))
