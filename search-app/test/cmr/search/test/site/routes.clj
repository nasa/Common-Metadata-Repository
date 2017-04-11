(ns cmr.search.test.site.routes
  (:require [clojure.test :refer :all]
            [cmr.search.site.routes :as r])
  (:use ring.mock.request))

(def ^:private site (#'cmr.search.site.routes/build-routes
                     {:public-conf {:protocol "https"
                                    :relative-root-url "/search"}}))

(defn- substring?
  [test-value string]
  (.contains string test-value))

(deftest cmr-welcome-page
  (testing "visited on a path without a trailing slash"
    (let [response (site (request :get "https://cmr.example.com/search"))]
      (testing "redirects permanently to the version with a trailing slash"
        (is (= (:status response) 200))
        (is (= (:headers response) {})))))

  (testing "visited on a path with a trailing slash"
    (let [response (site (request :get "https://cmr.example.com/search/"))]
      (testing "produces a HTTP 200 success response"
        (is (= (:status response) 200)))
      (testing "returns the welcome page HTML"
        (is (substring? "The CMR Search API" (:body response)))))))

(deftest landing-links-page
  (testing "landing links page returns content"
    (let [path "/site/collections/landing-pages"
          response (site (request :get (str "https://cmr.example.com/search" path)))]
      (is (= (:status response) 200))
      (is (substring? "Collections Landing Pages" (:body response)))
      (is (substring? "Landing Pages for EOSDIS Collections" (:body response))))))

(deftest eosdis-landing-links-page
  (testing "eosdis collections landing links page returns content"
    (let [path "/site/collections/eosdis-landing-pages"
          response (site (request :get (str "https://cmr.example.com/search" path)))]
      (is (= (:status response) 200))
      (is (substring? "Landing Pages for EOSDIS Collections" (:body response))))))

(deftest test-404
  (testing "a 404 is returned for a missing document"
    (is (= 404
           (:status
             (site
               (request
                 :get
                 "https://cmr.example.com/search/site/NOT-A-PAGE.html")))))))

(deftest cmr-api-documentation-page
  (let [response (site
                   (request
                     :get
                     "https://cmr.example.com/search/site/search_api_docs.html"))]
    (testing "uses the incoming host and scheme for its documentation endpoints"
      (is (substring?
            "https://cmr.example.com/search/collections" (:body response))))))
