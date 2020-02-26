(ns cmr.system-int-test.search.autocomplete-collection-facet-search-test
  "Integration tests for autocomplete collection search facets"
  (:require
    [clojure.test :refer :all]
    [cheshire.core :as json]
    [clojurewerkz.elastisch.rest :as esr]
    [clojurewerkz.elastisch.rest.document :as esd]
    [cmr.system-int-test.utils.url-helper :as url]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.search-util :as search]))

(defn autocomplete-fixture
  [f]
  (let [conn (esr/connect (url/elastic-root))
        foo (esd/create conn "1_autocomplete" "suggestion" {:value "foo" :type "instrument"})
        bar (esd/create conn "1_autocomplete" "suggestion" {:value "bar" :type "platform"})
        baz (esd/create conn "1_autocomplete" "suggestion" {:value "baz" :type "instrument"})]
    (index/wait-until-indexed)
    (f)
    (esd/delete conn "1_autocomplete" "suggestion" (:_id foo))
    (esd/delete conn "1_autocomplete" "suggestion" (:_id bar))
    (esd/delete conn "1_autocomplete" "suggestion" (:_id baz))
    (index/wait-until-indexed)))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture)
                      autocomplete-fixture]))

(deftest autocomplete-response-test
  (testing "response form test"
     (let [response (search/get-autocomplete-suggestions "q=foo")
           body (:body response)
           data (json/parse-string body true)]
       (is (contains? data :feed))
       (is (contains? (:feed data) :entry))))

  (testing "returns CMR-Hits header"
     (let [response (search/get-autocomplete-suggestions "q=foo")
           headers (:headers response)]
       (is (:CMR-Hits headers))
       (is (= "1" (:CMR-Hits headers))))))

(deftest autocomplete-functionality-test
  (testing "partial value match search"
   (let [response (search/get-autocomplete-suggestions "q=f")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:entry (:feed data)))))))

  (testing "full value match search"
   (let [response (search/get-autocomplete-suggestions "q=foo")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:entry (:feed data)))))))

  (testing "full value with extra value match search"
   (let [response (search/get-autocomplete-suggestions "q=foos")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 0 (count (:entry (:feed data)))))))

  (testing "case sensitivity test"
   (let [response (search/get-autocomplete-suggestions "q=FOO")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:entry (:feed data)))))))

  (testing "bad value search with no results"
   (let [response (search/get-autocomplete-suggestions "q=zee")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 0 (count (:entry (:feed data)))))))

  (testing "search with type filter"
   (let [response (search/get-autocomplete-suggestions "q=foo&type[]=instrument")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 1 (count (:entry (:feed data)))))))

  (testing "search with multiple types filter"
   (let [response (search/get-autocomplete-suggestions
                    "q=b&type[]=instrument&type[]=platform&type[]=project&type[]=provider")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 2 (count (:entry (:feed data)))))))

  (testing "search with type filter with no results"
   (let [response (search/get-autocomplete-suggestions "q=foo&type[]=platform")
         body (:body response)
         data (json/parse-string body true)]
     (is (= 0 (count (:entry (:feed data))))))))

(deftest autocomplete-usage-test
  (testing "missing query param response test"
   (let [response (search/get-autocomplete-suggestions nil {:throw-exceptions false})]
     (is (= 400 (:status response)))))

  (testing "empty string query response test"
   (let [response (search/get-autocomplete-suggestions "q=  " {:throw-exceptions false})]
     (is (= 400 (:status response)))))

  (testing "page_size 0 test"
   (let [response (search/get-autocomplete-suggestions "q=*&page_size=0")
         headers (:headers response)
         body (json/parse-string (:body response) true)
         entry (:entry (:feed body))]
     (is (= "3" (:CMR-Hits headers)))
     (is (= 0 (count entry)))))

  (testing "page_size 2 test"
   (let [response (search/get-autocomplete-suggestions "q=*&page_size=2")
         headers (:headers response)
         body (json/parse-string (:body response) true)
         entry (:entry (:feed body))]
     (is (= "3" (:CMR-Hits headers)))
     (is (= 2 (count entry)))))

  (testing "page_num should yield different 'first' results test"
   (let [page-one-response (search/get-autocomplete-suggestions "q=b&page_size=1")
         page-one-headers  (:headers page-one-response)
         page-one-body     (json/parse-string (:body page-one-response) true)
         page-one-entry    (first (:entry (:feed page-one-body)))

         page-two-response (search/get-autocomplete-suggestions "q=b&page_size=1&page_num=1")
         page-two-headers  (:headers page-two-response)
         page-two-body     (json/parse-string (:body page-two-response) true)
         page-two-entry    (first (:entry (:feed page-two-body)))]
     (is (not (= page-one-entry page-two-entry))))))
