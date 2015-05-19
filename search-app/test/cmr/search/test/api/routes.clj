(ns cmr.search.test.api.routes
  (:require [clojure.test :refer :all]
            [cmr.search.api.routes :as r])
  (:use ring.mock.request))

(def ^:private api (#'cmr.search.api.routes/build-routes
                     {:search-public-conf {:protocol "https" :relative-root-url "/search"}}))

(defn- substring?
  [test-value string]
  (.contains string test-value))

(deftest cmr-welcome-page
  (testing "visited on a path without a trailing slash"
    (let [response (api (request :get "https://cmr.example.com/search"))]
      (testing "redirects permanently to the version with a trailing slash"
        (is (= (:status response) 301))
        (is (= (:headers response) {"Location" "https://cmr.example.com/search/"})))))

  (testing "visited on a path with a trailing slash"
    (let [response (api (request :get "https://cmr.example.com/search/"))]
      (testing "produces a HTTP 200 success response"
        (is (= (:status response) 200)))
      (testing "returns the welcome page HTML"
        (is (substring? "The CMR Search API" (:body response)))))))

(deftest cmr-api-documentation-page
  (let [response (api (request :get "https://cmr.example.com/search/site/search_api_docs.html"))]
    (testing "uses the incoming host and scheme for its documentation endpoints"
      (is (substring? "https://cmr.example.com/search/collections" (:body response))))))

(deftest find-query-str-mixed-arity-param
  (testing "find-query-str-mixed-arity-param finds parameter with mixed arity correctly"
    (are [query-str found]
         (= found
            (r/find-query-str-mixed-arity-param query-str))

         "foo=1&foo[bar]=2" "foo"
         "foo[]=1&foo[bar]=2" "foo"
         "foo=0&foo[]=1&foo[]=2" nil
         "foo[bar]=1&foo[x]=2" nil
         "foo=1&options[foo][pattern]=true" nil
         "foo[]=1&options[foo][pattern]=true" nil)))