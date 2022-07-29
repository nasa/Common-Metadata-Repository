(ns cmr.search.test.services.query-execution
  "This tests the query-execution namespace. The main point of this is to make sure that specific
  queries are given the correct execution strategy."
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.services.search.params :as pc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.search.services.query-execution :as search-qe]))


(defn- params->query-execution-strategy
  "Converts parameters to a query and then returns the query execution strategy used on that."
  [concept-type params]
  (-> (pc/parse-parameter-query nil concept-type params)
      (#'qe/query->execution-strategy)))


;; Integration test that specific set of input parameters will result in query with the specified
;; execution strategy
(deftest parameters-to-query-execution-strategy
  (testing "Collection and Granule Common"
    (doseq [concept-type [:granule :collection]]
      (testing (str "Parameters for " concept-type)
        (are [params expected-strategy]
             (is (= expected-strategy (params->query-execution-strategy concept-type params)))

          ;; Specific elastic items queries by format
          {:concept-id "G1-PROV1" :result-format :atom} :specific-elastic-items
          {:concept-id "G1-PROV1" :result-format :json} :specific-elastic-items
          {:concept-id "G1-PROV1" :result-format :csv} :specific-elastic-items
          {:concept-id "G1-PROV1" :result-format :opendata} :specific-elastic-items

          ;; Multiple are supported
          {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :opendata} :specific-elastic-items

          ;; Sorting is supported
          {:concept-id ["G1-PROV1" "G2-PROV1"]
           :result-format :opendata
           :sort-key ["concept-id"]}
          :specific-elastic-items

          ;; A page size less than the number of items forces elastic strategy
          {:concept-id ["G1-PROV1" "G2-PROV1"]
           :page-size 2
           :result-format :opendata}
          :specific-elastic-items

          {:concept-id ["G1-PROV1" "G2-PROV1"]
           :page-size 1
           :result-format :opendata}
          :elasticsearch

          ;; A different page number forces elastic strategy
          {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :opendata :page-num 2} :elasticsearch

          ;; XML References use elastic strategy
          {:concept-id "G1-PROV1" :result-format :xml} :elasticsearch

          ;; Sorting uses the elastic strategy
          {:concept-id ["G1-PROV1" "G2-PROV1"]
           :result-format :echo10
           :sort-key ["concept-id"]}
          :elasticsearch

          {:concept-id ["G1-PROV1" "G2-PROV1"]
           :page-size 1
           :result-format :echo10}
          :elasticsearch

          ;; A different page number forces elastic strategy
          {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :echo10 :page-num 2} :elasticsearch))))



  (testing "Collection Specific"
    (are [params expected-strategy]
         (is (= expected-strategy (params->query-execution-strategy :collection params)))

      ;; Facets are supported
      {:concept-id "G1-PROV1" :result-format :opendata :include-facets "true"} :specific-elastic-items

      ;; Facets require elastic strategy
      {:concept-id "G1-PROV1" :result-format :echo10 :include-facets "true"} :elasticsearch

      ;; All revisions uses elastic strategy
      {:concept-id "C1-PROV1" :result-format :opendata :all-revisions "true"} :elasticsearch
      {:concept-id "C1-PROV1" :result-format :echo10 :all-revisions "true"} :elasticsearch

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; Metadata  queries
      {:concept-id "C1-PROV1" :result-format :echo10} :elasticsearch
      {:concept-id "C1-PROV1" :result-format :dif} :elasticsearch
      {:concept-id "C1-PROV1" :result-format :dif10} :elasticsearch
      {:concept-id "C1-PROV1" :result-format :iso19115} :elasticsearch
      {:concept-id "C1-PROV1" :result-format :iso-smap} :elasticsearch))

  (testing "Granule Specific"
    (are [params expected-strategy]
         (is (= expected-strategy (params->query-execution-strategy :granule params)))

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; Direct db queries
      {:concept-id "G1-PROV1" :result-format :echo10} :direct-db
      {:concept-id "G1-PROV1" :result-format :dif} :direct-db
      {:concept-id "G1-PROV1" :result-format :dif10} :direct-db
      {:concept-id "G1-PROV1" :result-format :iso19115} :direct-db
      {:concept-id "G1-PROV1" :result-format :iso-smap} :direct-db

      ;; Multiple are supported
      {:concept-id ["G1-PROV1" "G2-PROV1"] :result-format :echo10} :direct-db

      ;; A page size less than the number of items forces elastic strategy
      {:concept-id ["G1-PROV1" "G2-PROV1"]
       :page-size 2
       :result-format :echo10}
      :direct-db)))

(def orig-facets {:facets {:title "Browse Collections",
                           :type :group,
                           :has_children true,
                           :children [{:title "Platforms",
                                       :type :group,
                                       :applied true,
                                       :has_children true,
                                       :children [{:title "Space-based Platforms",
                                                   :type :filter,
                                                   :applied true,
                                                   :count 4569,
                                                   :links {:remove "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=PlanetScope"},
                                                   :has_children true,
                                                   :children [{:title "Earth Observation Satellites",
                                                               :type :filter,
                                                               :applied true,
                                                               :count 4195,
                                                               :links {:remove "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=PlanetScope&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
                                                               :has_children true,
                                                               :children [{:title "Worldview",
                                                                           :type :filter,
                                                                           :applied false,
                                                                           :count 3,
                                                                           :links {:apply "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&platforms_h%5B0%5D%5Bsub_category%5D=Worldview&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=PlanetScope&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
                                                                           :has_children true}]}]}
                                                  {:title "PlanetScope",
                                                   :type :filter,
                                                   :applied true,
                                                   :count 0,
                                                   :links {:remove "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
                                                   :has_children false}]}]}})

(def all-facets {:facets {:title "Browse Collections",
                          :type :group,
                          :has_children true,
                          :children [{:title "Platforms",
                                      :type :group,
                                      :applied true,
                                      :has_children true,
                                      :children [{:title "Space-based Platforms",
                                                  :type :filter,
                                                  :applied true,
                                                  :count 4569,
                                                  :links {:remove "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=PlanetScope"},
                                                  :has_children true,
                                                  :children [{:title "Earth Observation Satellites",
                                                              :type :filter,
                                                              :applied true,
                                                              :count 4195,
                                                              :links {:remove "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=PlanetScope&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
                                                              :has_children true,
                                                              :children [{:title "PlanetScope",
                                                                          :type :filter,
                                                                          :applied true,
                                                                          :count 2,
                                                                          :links {:remove "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
                                                                          :has_children false}
                                                                         {:title "Worldview",
                                                                          :type :filter,
                                                                          :applied false,
                                                                          :count 3,
                                                                          :links {:apply "https://cmr.sit.earthdata.nasa.gov:443/search/collections.json?page_num=1&include_granule_counts=true&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&sort_key%5B%5D=has_granules_or_cwic&sort_key%5B%5D=-usage_score&platforms_h%5B0%5D%5Bsub_category%5D=Worldview&page_size=20&include_has_granules=true&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=PlanetScope&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
                                                                          :has_children true}]}]}]}]}})

(deftest update-facets
  (testing "does not return nils"
    (let [update-facets #'search-qe/update-facets
          result (update-facets orig-facets all-facets)]
      (is (= (count result) 1))
      (is (every? some? result)))))
