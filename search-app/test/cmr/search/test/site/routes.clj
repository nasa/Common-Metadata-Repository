(ns cmr.search.test.site.routes
  (:require [clojure.test :refer :all]
            [cmr.search.site.routes :as r])
  (:use ring.mock.request))

(def ^:private site (#'cmr.search.site.routes/build-routes
                     {:public-conf {:protocol "https"
                                    :relative-root-url "/search"}}))

(def base-url "https://cmr.example.com/search")

(defn- substring?
  [test-value string]
  (.contains string test-value))

(deftest cmr-welcome-page
  (testing "visited on a path without a trailing slash"
    (let [response (site (request :get base-url))]
      (testing "redirects permanently to the version with a trailing slash"
        (is (= (:status response) 200))
        (is (= (:headers response) {})))))

  (testing "visited on a path with a trailing slash"
    (let [response (site (request :get base-url))]
      (testing "produces a HTTP 200 success response"
        (is (= (:status response) 200)))
      (testing "returns the welcome page HTML"
        (is (substring? "The CMR Search API" (:body response)))))))

(deftest collections-directory-page
  (testing "collections directory page returns content"
    (let [url-path "/site/collections/directory"
          response (site (request :get (str base-url url-path)))]
      (is (= (:status response) 200))
      (testing "page title is correct"
        (is (substring? "Directory of Collections Landing Pages"
                        (:body response))))
      (testing "page has a link to EOSDIS collections directory"
        (is (substring? "Directory for EOSDIS Collections"
                        (:body response)))))))

(deftest eosdis-collections-directory-page
  (testing "eosdis collections collections directory page returns content"
    (let [url-path "/site/collections/directory/eosdis"
          response (site (request :get (str base-url url-path)))]
      (is (= (:status response) 200))
      (is (substring? "Directory of Landing Pages for EOSDIS Collections"
                      (:body response))))))

(deftest test-404
  (testing "a 404 is returned for a missing document"
    (is (= 404
           (->> "/site/NOT-A-PAGE.html"
                (str base-url)
                (request :get)
                (site)
                :status)))))

(deftest cmr-api-documentation-page
  (let [response (site
                   (request
                     :get (str base-url "/site/search_api_docs.html")))]
    (testing "uses the incoming host and scheme for the docs endpoints"
      (is (substring?
            "https://cmr.example.com/search/collections" (:body response))))))
