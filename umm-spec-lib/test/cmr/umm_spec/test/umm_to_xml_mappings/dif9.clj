(ns cmr.umm-spec.test.umm-to-xml-mappings.dif9
  "Tests to verify that dif9 records are generated correctly."
  (:require [cmr.umm-spec.umm-to-xml-mappings.dif9 :as dif9]
            [cmr.umm-spec.umm-to-xml-mappings.dif9.spatial-extent :as spatial]
            [clojure.test :refer :all]))

(def expected-full-double-vertical-coverage
  [{:Minimum_Altitude "0",
    :Maximum_Altitude "100",
    :Minimum_Depth "-1",
    :Maximum_Depth "-100"}
   {:Minimum_Altitude "0",
    :Maximum_Altitude "100",
    :Minimum_Depth "-1",
    :Maximum_Depth "-100"}])

(def full-double-vertical-coverage
  {:SpatialExtent
   {:VerticalSpatialDomains [{:Type "Minimum Altitude"
                              :Value "0"}
                             {:Type "Maximum Altitude"
                              :Value "100"}
                             {:Type "Minimum Depth"
                              :Value "-1"}
                             {:Type "Maximum Depth"
                              :Value "-100"}
                             {:Type "Minimum Altitude"
                              :Value "0"}
                             {:Type "Maximum Altitude"
                              :Value "100"}
                             {:Type "Minimum Depth"
                              :Value "-1"}
                             {:Type "Maximum Depth"
                              :Value "-100"}]}})

(def expected-partial-vertical-coverage
    [{:Minimum_Altitude "0",
      :Maximum_Altitude "100",
      :Minimum_Depth "-1",
      :Maximum_Depth "-100"}
     {:Minimum_Altitude "0",
      :Maximum_Altitude "100"}])

(def partial-vertical-coverage
  {:SpatialExtent
   {:VerticalSpatialDomains [{:Type "Minimum Altitude"
                              :Value "0"}
                             {:Type "Maximum Altitude"
                              :Value "100"}
                             {:Type "Minimum Depth"
                              :Value "-1"}
                             {:Type "Maximum Depth"
                              :Value "-100"}
                             {:Type "Minimum Altitude"
                              :Value "0"}
                             {:Type "Maximum Altitude"
                              :Value "100"}]}})

(def expected-bad-data-vertical-coverage
  [{:Minimum_Altitude "0"}])

(def bad-data-vertical-coverage
   {:SpatialExtent
    {:VerticalSpatialDomains [{:Type "Some type of Altitude"
                               :Value "0"}
                              {:Type "Some type of Maximum"
                               :Value "100"}
                              {:Type "Some type of data"
                               :Value "-1"}
                              {:Type "Some other type of data"
                               :Value "-100"}
                              {:Type "Minimum Aircraft Altitude"
                               :Value "0"}]}})

(def expected-mixed-data-vertical-coverage
  [{:Minimum_Altitude "0",
    :Maximum_Altitude "100"}
   {:Minimum_Altitude "50", 
    :Maximum_Altitude "70"}])

(def mixed-data-vertical-coverage
   {:SpatialExtent
    {:VerticalSpatialDomains [{:Type "Minimum Altitude"
                               :Value "0"}
                              {:Type "Maximum Altitude"
                               :Value "100"}
                              {:Type "Some type"
                               :Value "Some value"}
                              {:Type "Minimum Altitude"
                               :Value "50"}
                              {:Type "Maximum Altitude"
                               :Value "70"}]}})

(deftest test-dif9-double-vertical-extent

  (testing "Full Double Vertical Coverage test"
    (is (= expected-full-double-vertical-coverage
           (spatial/create-vertical-domain-vector-maps full-double-vertical-coverage))))

  (testing "Partial Vertical Coverage test"
    (is (= expected-partial-vertical-coverage
           (spatial/create-vertical-domain-vector-maps partial-vertical-coverage))))

  (testing "Bad Data Vertical Coverage test"
    (is (= expected-bad-data-vertical-coverage
           (spatial/create-vertical-domain-vector-maps bad-data-vertical-coverage))))

  (testing "Mixed Data Vertical Coverage test"
    (is (= expected-mixed-data-vertical-coverage
           (spatial/create-vertical-domain-vector-maps mixed-data-vertical-coverage)))))
