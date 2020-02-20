(ns cmr.system-int-test.search.autocomplete-collection-facet-search-test
  "Integration tests for autocomplete collection search facets"
  (:require
    [clojure.test :refer :all]
    [cheshire.core :as json]
    [clojurewerkz.elastisch.rest :as esr]
    [clojurewerkz.elastisch.rest.document :as esd]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.search-util :as search]))

(defn autocomplete-fixture
  [f]
  (let [conn (esr/connect "http://localhost:9210")
        foo (esd/create conn "1_autocomplete" "suggestion" {:value "foo" :type "instrument"})
        bar (esd/create conn "1_autocomplete" "suggestion" {:value "bar" :type "platform"})
        baz (esd/create conn "1_autocomplete" "suggestion" {:value "baz" :type "instrument"})]
    (index/wait-until-indexed)
    (f)))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))
(use-fixtures :each autocomplete-fixture)

(deftest autocomplete-suggest-test
  (testing "returned result form test"
   (let [response (search/get-autocomplete-suggestions "foo")
         body (:body response)
         data (json/parse-string body true)]
     (is (contains? data :query))
     (is (contains? data :results))
     (is (contains? (:results data) :items))))

  (testing "missing query param response test"
   (let [response (search/get-autocomplete-suggestions)]
     (is (= 400 (:status response)))))

  (testing "partial value match search"
   (let [response (search/get-autocomplete-suggestions "f")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:items (:results data)))))))

  (testing "full value match search"
   (let [response (search/get-autocomplete-suggestions "foo")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:items (:results data)))))))

  (testing "full value with extra value match search"
   (let [response (search/get-autocomplete-suggestions "foos")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 0 (count (:items (:results data)))))))

  (testing "case sensitivity test"
   (let [response (search/get-autocomplete-suggestions "FOO")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:items (:results data)))))))

  (testing "bad value search with no results"
   (let [response (search/get-autocomplete-suggestions "zee")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 0 (count (:items (:results data)))))))

  (testing "search with type filter"
   (let [response (search/get-autocomplete-suggestions "foo" ["instrument"])
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:items (:results data)))))))

  (testing "search with multiple types filter"
   (let [response (search/get-autocomplete-suggestions "foo" ["instrument" "platform" "project" "provider"])
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:items (:results data)))))))

  (testing "search with type filter with no results"
   (let [response (search/get-autocomplete-suggestions "foo" ["platform"])
         body (:body response)
         data (json/parse-string body true)]
     (is (= 0 (count (:items (:results data))))))))
