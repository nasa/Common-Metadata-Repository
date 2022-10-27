(ns cmr.search.test.site.routes
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.test.side-api :as side-api]
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

(deftest search-welcome-page
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

(deftest collections-directory-page
  (testing "collections directory page returns content"
    (let [url-path "/site/collections/directory"
          response (site (request :get (str base-url url-path)))]
      (is (= 200 (:status response)))
      (testing "page title is correct"
        (is (string/includes?
             (:body response)
             "Provider Holdings Directory")))
      (testing "page has a link to EOSDIS collections directory"
        (is (string/includes?
             (:body response)
             "EOSDIS Collections"))))))

(deftest test-404
  (testing "a 404 is returned for a missing document"
    (is (= 404
           (->> "/site/NOT-A-PAGE.html"
                (str base-url)
                (request :get)
                (site)
                :status)))
    (is (= 404
           (->> "/site/docs/search/NOT-A-PAGE.html"
                (str base-url)
                (request :get)
                (site)
                :status)))))

(deftest search-api-documentation-page
  (let [response (site
                   (request
                     :get (str base-url "/site/docs/search/api.html")))]
    (testing "uses the incoming host and scheme for the docs endpoint"
      (is (= 200 (:status response)))
      (is (string/includes?
           (:body response)
           "API Documentation")))))

(deftest search-site-documentation-page
  (let [response (site
                   (request
                     :get (str base-url "/site/docs/search/site.html")))]
    (testing "uses the incoming host and scheme for the docs endpoint"
      (is (= 200 (:status response)))
      (is (string/includes?
           (:body response)
           "Site Routes &amp; Web Resources Documentation")))))

(deftest search-all-documentation-page
  (let [response (site (request :get (str base-url "/site/docs/search")))]
    (testing "all docs links appear here"
      (is (= 200 (:status response)))
      (is (string/includes?
           (:body response)
           "Documentation for CMR Search"))
      (is (or
           ;; local build
           (string/includes?
            (:body response)
            "v dev")
           ;; ci build
           (string/includes?
            (:body response)
            "v %CMR-RELEASE-VERSION%")))
      (is (string/includes?
           (:body response)
           "site/docs/search/api"))
      (is (string/includes?
           (:body response)
           "site/docs/search/site")))))

(deftest search-url-reorg-redirects
  (let [response (site (request :get (str base-url "/site/docs/search/api")))]
    (testing "clean docs URL for api docs performs redirect"
      (is (= 307 (:status response)))
      (is (string/includes?
            (get-in response [:headers "Location"])
            "site/docs/search/api.html"))))
  (let [response (site (request :get (str base-url "/site/docs/search/site")))]
    (testing "clean docs URL for site docs performs redirect"
      (is (= 307 (:status response)))
      (is (string/includes?
           (get-in response [:headers "Location"])
           "site/docs/search/site.html"))))
  (let [response (site (request :get (str base-url "/site/search_api_docs.html")))]
    (testing "clean docs URL for api docs performs redirect"
      (is (= 301 (:status response)))
      (is (string/includes?
            (get-in response [:headers "Location"])
            "site/docs/search/api.html"))))
  (let [response (site (request :get (str base-url "/site/search_site_docs.html")))]
    (testing "clean docs URL for site docs performs redirect"
      (is (= 301 (:status response)))
      (is (string/includes?
            (get-in response [:headers "Location"])
            "site/docs/search/site.html")))))

(deftest options-test
  (testing "Testing the functions in the options map"
    (is (= ((get r/options :spacer) 3) 0))
    (is (= ((get r/options :spacer) 4) 4))
    (is (= ((get r/options :spacer) 5) 8))
    ;;Any umpapped number will default to 0
    (is (= ((get r/options :spacer) 2) 0))))
