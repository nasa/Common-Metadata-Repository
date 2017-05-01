(ns cmr.search.test.site.routes
  (:require [clojure.test :refer :all]
            [cmr.search.site.routes :as r]
            [ring.mock.request :refer [request]]))

(def ^:private scheme "https")
(def ^:private host "cmr.example.com")
(def ^:private base-path "/search")
(def ^:private base-url (format "%s://%s%s" scheme host base-path))
(def ^:private system {:public-conf
                        {:protocol scheme
                         :host host
                         :relative-root-url base-path}})
(def ^:private site (#'cmr.search.site.routes/build-routes system))

(defn- substring?
  [test-value string]
  (.contains string test-value))

(deftest cmr-welcome-page
  (testing "visited on a path without a trailing slash"
    (let [response (site (request :get base-url))]
      (testing "produces a HTTP 200 success response"
        (is (= (:status response) 200)))
      (testing "returns the welcome page HTML"
        (is (substring? "The Common Metadata Repository"
                        (:body response))))))
  (testing "visited on a path with a trailing slash"
    (let [response (site (request :get (str base-url "/")))]
      (testing "produces a HTTP 200 success response"
        (is (= (:status response) 200)))
      (testing "returns the welcome page HTML"
        (is (substring? "The Common Metadata Repository"
                        (:body response)))))))

(deftest collections-directory-page
  (testing "collections directory page returns content"
    (let [url-path "/site/collections/directory"
          response (site (request :get (str base-url url-path)))]
      (is (= (:status response) 200))
      (testing "page title is correct"
        (is (substring? "Directory of Collections Landing Pages"
                        (:body response))))
      (testing "page has a link to EOSDIS collections directory"
        (is (substring? "EOSDIS Collections"
                        (:body response)))))))

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
                     :get (str base-url "/site/docs/api.html")))]
    (testing "uses the incoming host and scheme for the docs endpoint"
      (is (= (:status response) 200))
      (is (substring? "API Documentation" (:body response))))))

(deftest cmr-site-documentation-page
  (let [response (site
                   (request
                     :get (str base-url "/site/docs/site.html")))]
    (testing "uses the incoming host and scheme for the docs endpoint"
      (is (= (:status response) 200))
      (is (substring?
            "Site Routes &amp; Web Resources Documentation" (:body response))))))

(deftest cmr-all-documentation-page
  (let [response (site (request :get (str base-url "/site/docs")))]
    (testing "all docs links appear here"
      (is (= (:status response) 200))
      (is (substring? "Documentation for CMR Search" (:body response)))
      (is (substring? "site/docs/api" (:body response)))
      (is (substring? "site/docs/site" (:body response))))))

(deftest cmr-url-reorg-redirects
  (let [response (site (request :get (str base-url "/site/docs/api")))]
    (testing "clean docs URL for api docs performs redirect"
      (is (= (:status response) 307))
      (is (substring?
            "site/docs/api.html"
            (get-in response [:headers "Location"])))))
  (let [response (site (request :get (str base-url "/site/docs/site")))]
    (testing "clean docs URL for site docs performs redirect"
      (is (= (:status response) 307))
      (is (substring?
            "site/docs/site.html"
            (get-in response [:headers "Location"])))))
  (let [response (site (request :get (str base-url "/site/search_api_docs.html")))]
    (testing "clean docs URL for api docs performs redirect"
      (is (= (:status response) 301))
      (is (substring?
            "site/docs/api.html"
            (get-in response [:headers "Location"])))))
  (let [response (site (request :get (str base-url "/site/search_site_docs.html")))]
    (testing "clean docs URL for site docs performs redirect"
      (is (= (:status response) 301))
      (is (substring?
            "site/docs/site.html"
            (get-in response [:headers "Location"]))))))
