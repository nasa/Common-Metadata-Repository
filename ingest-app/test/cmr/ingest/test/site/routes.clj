(ns cmr.ingest.test.site.routes
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.ingest.site.routes :as r]
   [ring.mock.request :refer [request]]))

(def ^:private scheme "https")
(def ^:private host "cmr.example.com")
(def ^:private base-path "")
(def ^:private base-url (format "%s://%s%s" scheme host base-path))
(def ^:private system {:public-conf
                        {:protocol scheme
                         :host host
                         :relative-root-url base-path}})
(def ^:private site (#'cmr.ingest.site.routes/build-routes system))

(deftest ingest-welcome-page
  (testing "visited on a path without a trailing slash"
    (let [response (site (request :get base-url))]
      (testing "produces a HTTP 200 success response"
        (is (= 200 (:status response))))
      (testing "returns the welcome page HTML"
        (is (string/includes?
             (:body response)
             "The Common Metadata Repository")))))
  (testing "visited on a path with a trailing slash"
    (let [response (site (request :get (str base-url "/")))]
      (testing "produces a HTTP 200 success response"
        (is (= 200 (:status response))))
      (testing "returns the welcome page HTML"
        (is (string/includes?
             (:body response)
             "The Common Metadata Repository"))))))

(deftest test-404
  (testing "a 404 is returned for a missing document"
    (is (= 404
           (->> "/site/NOT-A-PAGE.html"
                (str base-url)
                (request :get)
                (site)
                :status)))
    (is (= 404
           (->> "/site/docs/ingest/NOT-A-PAGE.html"
                (str base-url)
                (request :get)
                (site)
                :status)))))

(deftest ingest-api-documentation-page
  (let [response (site
                   (request
                     :get (str base-url "/site/docs/ingest/api.html")))]
    (testing "uses the incoming host and scheme for the docs endpoint"
      (is (= 200 (:status response)))
      (is (string/includes? (:body response) "API Documentation")))))

(deftest ingest-site-documentation-page
  (let [response (site
                   (request
                     :get (str base-url "/site/docs/ingest/site.html")))]
    (testing "uses the incoming host and scheme for the docs endpoint"
      (is (= 200 (:status response)))
      (is (string/includes?
           (:body response)
           "Site Routes &amp; Web Resources Documentation")))))

(deftest ingest-all-documentation-page
  (let [response (site (request :get (str base-url "/site/docs/ingest")))]
    (testing "all docs links appear here"
      (is (= 200 (:status response)))
      (is (string/includes? (:body response) "Documentation for CMR Ingest"))
      (is (string/includes? (:body response) "site/docs/ingest/api"))
      (is (string/includes? (:body response) "site/docs/ingest/site")))))

(deftest ingest-url-reorg-redirects
  (let [response (site (request :get (str base-url "/site/docs/ingest/api")))]
    (testing "clean docs URL for api docs performs redirect"
      (is (= 307 (:status response)))
      (is (string/includes?
           (get-in response [:headers "Location"])
           "site/docs/ingest/api.html"))))
  (let [response (site (request :get (str base-url "/site/docs/ingest/site")))]
    (testing "clean docs URL for site docs performs redirect"
      (is (= 307 (:status response)))
      (is (string/includes?
           (get-in response [:headers "Location"])
           "site/docs/ingest/site.html"))))
  (let [response (site (request :get (str base-url "/site/ingest_api_docs.html")))]
    (testing "clean docs URL for api docs performs redirect"
      (is (= 301 (:status response)))
      (is (string/includes?
           (get-in response [:headers "Location"])
           "site/docs/ingest/api.html")))))
(deftest options-test
  (testing "Testing the functions in the options map"
    (is (= ((get r/options :spacer) 2) 0))
    (is (= ((get r/options :spacer) 3) 8))
    (is (= ((get r/options :spacer) 4) 4))
        ;;Any umpapped number will default to 0
    (is (= ((get r/options :spacer) 5) 0))))
