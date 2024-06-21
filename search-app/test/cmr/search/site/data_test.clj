(ns cmr.search.site.data-test
  (:require
   [clojure.test :refer :all]
   [clj-http.client :as client]
   [cmr.search.site.data :as data]
   [cmr.search.site.util :as util]
   [cmr.transmit.config :as t-config]))

(deftest app-url->virtual-directory-url-test
  (are [input expected]
      (= expected
         (data/app-url->virtual-directory-url input))

    ;; PROD
    "https://cmr.earthdata.nasa.gov/search"
    "https://cmr.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.earthdata.nasa.gov/search/"
    "https://cmr.earthdata.nasa.gov/virtual-directory/"

    ;; SIT
    "https://cmr.sit.earthdata.nasa.gov/search"
    "https://cmr.sit.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.sit.earthdata.nasa.gov/search/"
    "https://cmr.sit.earthdata.nasa.gov/virtual-directory/"

    ;; UAT
    "https://cmr.uat.earthdata.nasa.gov/search"
    "https://cmr.uat.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.uat.earthdata.nasa.gov/search/"
    "https://cmr.uat.earthdata.nasa.gov/virtual-directory/"

    ;; localhost - no change expected
    "http://localhost:3003"
    "http://localhost:3003"))

(deftest app-url->stac-urls-test
  (are [input expected]
      (= expected
         (data/app-url->stac-urls input))

    ;; PROD
    "https://cmr.earthdata.nasa.gov:443/search"
    {:stac-url "https://cmr.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.earthdata.nasa.gov/static-cloudstac"}

    "https://cmr.earthdata.nasa.gov:443/search/"
    {:stac-url "https://cmr.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.earthdata.nasa.gov/static-cloudstac"}

    ;; SIT
    "https://cmr.sit.earthdata.nasa.gov:443/search"
    {:stac-url "https://cmr.sit.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.sit.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.sit.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.sit.earthdata.nasa.gov/static-cloudstac"}

    "https://cmr.sit.earthdata.nasa.gov:443/search/"
    {:stac-url "https://cmr.sit.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.sit.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.sit.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.sit.earthdata.nasa.gov/static-cloudstac"}

    ;; UAT
    "https://cmr.uat.earthdata.nasa.gov:443/search"
    {:stac-url "https://cmr.uat.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.uat.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.uat.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.uat.earthdata.nasa.gov/static-cloudstac"}

    "https://cmr.uat.earthdata.nasa.gov:443/search/"
    {:stac-url "https://cmr.uat.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.uat.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.uat.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.uat.earthdata.nasa.gov/static-cloudstac"}

    ;; localhost
    "http://localhost:3003"
    {:stac-url "http://localhost:3000/stac"
     :cloudstac-url "http://localhost:3000/cloudstac"
     :stac-docs-url "http://localhost:3000/stac/docs"
     :static-cloudstac-url "http://localhost:3000/static-cloudstac"}))

(deftest client-id-tests
  (testing "check for client id from inside of endpoint-get"
    ;; Construct a funky context to trigger the special branch inside of get-providers
    (let [context {:cmr-application :search :execution-context :cli}
          ;; Do the actual test inside a mocked function to check if clj-http.client gets client-id
          ;; from the calling CMR code without actually needing to send a message over the wire,
          ;; also return something valid that clj-http.client can understand
          action-tester (fn [arg]
                          (is (= "cmr-internal" (:client-id (:headers arg)))
                              (format "Failed testing %s %s" arg (:url arg)))
                          {:status 200 :body "" :headers {"CMR-Hits" "42"}})]
      ;; Mock the request function which get,put,post,delete use inside clj-http.client
      (with-redefs [client/request action-tester]
        ;; First test (by calling) the util function endpoint-get for basic functionality, then test
        ;; (by calling) the function get-providers to ensure that client-id is passed from
        ;; get-providers to endpoint-get and then to client/request.
        (let [result (util/endpoint-get "http://localhost:3003"
                                        {:body "something"
                                         :headers {:client-id t-config/cmr-client-id}})]
          (cmr.search.site.data/get-providers context)
          ;; This test is just to trigger the testing function because the real test is inside a
          ;; mocked function which goes un-noticed without the next line.
          (is (not (true? result))))))))
