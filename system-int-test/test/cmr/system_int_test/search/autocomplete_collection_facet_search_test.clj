(ns cmr.system-int-test.search.autocomplete-collection-facet-search-test
  "Integration tests for autocomplete collection search facets"
  (:require
    [clojure.test :refer :all]
    [cheshire.core :as json]
    [clojurewerkz.elastisch.rest :as esr]
    [clojurewerkz.elastisch.rest.document :as esd]
    [cmr.common.util :refer [are3]]
    [cmr.system-int-test.utils.url-helper :as url]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.search-util :as search]))

(defn autocomplete-fixture
  [f]
  (let [conn (esr/connect (url/elastic-root))
        foo (esd/create conn "1_autocomplete" "suggestion" {:value "foo" :type "instrument"})
        bar (esd/create conn "1_autocomplete" "suggestion" {:value "bar" :type "platform"})
        baz (esd/create conn "1_autocomplete" "suggestion" {:value "baaz" :type "instrument"})]
    (index/wait-until-indexed)
    (f)
    (esd/delete conn "1_autocomplete" "suggestion" (:_id foo))
    (esd/delete conn "1_autocomplete" "suggestion" (:_id bar))
    (esd/delete conn "1_autocomplete" "suggestion" (:_id baz))
    (index/wait-until-indexed)))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture)
                      autocomplete-fixture]))

;;(deftest test-autocomplete-short
;; (testing "the test"
;;  (let [response (search/get-autocomplete-suggestions "q=b&type[]=platform")]
;;    (println (json/parse-string (:body response) true))
;;    (is (= 200 (:status response))))))

(defn- query->json-response-body
  ([query]
   (query->json-response-body query nil))
  ([query opts]
  (let [response (search/get-autocomplete-suggestions query opts)
        body (:body response)
        data (json/parse-string body true)]
    data)))

(defn- response-body->entries
  [data]
  (:entry (:feed data)))

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
       (is (:CMR-Took headers)))))

(deftest autocomplete-functionality-test
  (testing "value search"
   (are3
    [query expected]
    (is (= expected (count (response-body->entries (query->json-response-body query)))))

    "partial value"
    "q=f" 1

    "full value match"
    "q=foo" 1

    "full value with extra value match"
    "q=foos" 0

    "case sensitivity test"
    "q=FOO" 1

    "no result test"
    "q=zee" 0

    "search with type filter"
    "q=foo&type[]=instrument" 1

    "search with multiple types filter"
    "q=b&type[]=instrument&type[]=platform&type[]=project&type[]=provider" 2

    "search with type filter with no results"
    "q=foo&type[]=platform" 0)))

(deftest autocomplete-usage-test
  (testing "invalid or missing query param tests: "
   (are3
    [query]
    (is (= 400 (:status (search/get-autocomplete-suggestions query {:throw-exceptions false}))))

    "no query provided"
    nil

    "blank query provided"
    "q=  "

    "page_size too large"
    "q=ice&page_size=999999999"

    "page_size non-positive"
    "q=ice&page_size=-1"))

  (testing "page_size test"
   (are3
    [query expected cmr-hits]
    (do
      (let [response (search/get-autocomplete-suggestions query)
            headers (:headers response)
            body (json/parse-string (:body response) true)
            entry (:entry (:feed body))]
        (is (= (str cmr-hits) (:CMR-Hits headers)))
        (is (= expected (count entry)))))

    "page size 0"
    "q=*&page_size=0" 0 3

    "page size 1"
    "q=*&page_size=1" 1 3

    "page size 2"
    "q=*&page_size=2" 2 3

    "page size 3"
    "q=*&page_size=3" 3 3

    "page size 100"
    "q=*&page_size=100" 3 3))

  (testing "page_num should default to 1"
   (let [a (as-> (query->json-response-body "q=b") response
                 (first (response-body->entries response)))

         b (as-> (query->json-response-body "q=b&page_num=1") response
                 (first (response-body->entries response)))]
     (is (= a b))))

  (testing "page_num should yield different 'first' results test"
   (let [everything (response-body->entries (query->json-response-body "q=*&page_size=2"))
         _ (println everything)

          page-one-entry (as-> (query->json-response-body "q=b&page_size=2&page_num=1") response1
                              (response-body->entries response1))

         page-two-entry (as-> (query->json-response-body "q=b&page_size=2&page_num=2") response2
                              (response-body->entries response2))
         _ (println page-one-entry)
         _ (println page-two-entry)]
     (is (not= page-one-entry page-two-entry)))))
