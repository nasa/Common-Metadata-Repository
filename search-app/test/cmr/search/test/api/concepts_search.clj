(ns cmr.search.test.api.concepts-search
  "Tests to verify that the route building support functions are working properly"
  (:require [clojure.test :refer :all]
            [cmr.search.api.concepts-search :as concept-search]))

(deftest format-regex
  (testing "Testing that the regex is formatted for an individual concept"
   (is (= "(?grid)" (concept-search/format-regex "grid")))))

(deftest get-generics
  (testing "Ensuring that we retrieve the list of generics as keywords"
   (is (= [:grids
           :dataqualitysummaries
           :orderoptions
           :serviceentries
           :serviceoptions] concept-search/get-generics)))

  (deftest join-generic-concepts
    (testing "Ensuring that all generic concepts are joined together")
    (is (= "(?:grids)|(?:dataqualitysummaries)|(?:orderoptions)|(?:serviceentries)|(?:serviceoptions)" concept-search/join-generic-concepts))))

(deftest routes-regex
  (testing "Ensuring that the regex is fully formed for the routes for all concepts. Note this must be comapred as strings"
   (is (= (str #"(?:(?:granules)|(?:collections)|(?:variables)|(?:subscriptions)|(?:tools)|(?:services)|(?:grids)|(?:dataqualitysummaries)|(?:orderoptions)|(?:serviceentries)|(?:serviceoptions))(?:\..+)?") (str concept-search/routes-regex)))))
