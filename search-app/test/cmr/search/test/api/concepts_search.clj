(ns cmr.search.test.api.concepts_search
  "Tests to verify that the route building support functions are workign properly"
  (:require [clojure.test :refer :all]
            [cmr.search.api.concepts-search :as concept_search]))

(deftest plurlize-concept
  (testing "Teseting taking a concept and making is plural"
   (is (= :grids (concept_search/pluralize-concept :grid))))
  (is (= :dataqualitysummaries (concept_search/pluralize-concept :dataqualitysummary))))

(deftest format-regex
  (testing "Testing that the regex is formatted for an individual concept"
   (is (= "(?grid)" (concept_search/format-regex "grid")))))

(deftest get-generics
  (testing "Ensuring that retrieving the generics does not return :generic"
   (is (= [:grids
           :dataqualitysummaries
           :orderoptions
           :serviceentries
           :serviceoptions] concept_search/get-generics)))

  (deftest join-generic-concepts
    (testing "Ensuring that all generic concepts are joined together ")
    (is (= "(?:grids)|(?:dataqualitysummaries)|(?:orderoptions)|(?:serviceentries)|(?:serviceoptions)" concept_search/join-generic-concepts))))

(deftest routes-regex
  (testing "Ensuring that the regex is fully formed for the routes for all concepts. Note this must be comapred as a string"
   (is (= (str #"(?:(?:granules)|(?:collections)|(?:variables)|(?:subscriptions)|(?:tools)|(?:services)|(?:grids)|(?:dataqualitysummaries)|(?:orderoptions)|(?:serviceentries)|(?:serviceoptions))(?:\..+)?") (str concept_search/routes-regex)))))