(ns ^:integration cmr.opendap.tests.integration.app.admin
  "Note: this namespace is exclusively for integration tests; all tests defined
  here will use one or more integration test fixtures.

  Warning: To run the integration tests, you will need to create CMR/ECHO
  tokens and export these as shell environment variables. In particular, each
  token gets its own ENV var:
  * CMR_SIT_TOKEN
  * CMR_UAT_TOKEN
  * CMR_PROD_TOKEN

  Definition used for integration tests:
  * https://en.wikipedia.org/wiki/Software_testing#Integration_testing"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.testing.system :as test-system]
   [org.httpkit.client :as httpc])
  (:import
   (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(deftest admin-routes
  (testing "health route ..."
    (let [response @(httpc/get (format "http://localhost:%s/opendap/health"
                                       (test-system/http-port)))]
      (is (= 200 (:status response)))
      (is (= {:config {:ok? true}
              :httpd {:ok? true}
              :logging {:ok? false}}
             (json/parse-string (:body response) true)))))
  (testing "protected admin route ..."
    (let [response @(httpc/get (format "http://localhost:%s/opendap/ping"
                                       (test-system/http-port)))]
      (is (= 403 (:status response)))
      (is (= {:errors ["An ECHO token is required to access this resource."]}
             (response/parse-json-body (:body response)))))))

(deftest testing-routes
  (is 401
      (:status @(httpc/get (format "http://localhost:%s/opendap/testing/401"
                                   (test-system/http-port)))))
  (is 403
      (:status @(httpc/get (format "http://localhost:%s/opendap/testing/403"
                                   (test-system/http-port)))))
  (is 404
      (:status @(httpc/get (format "http://localhost:%s/opendap/testing/404"
                                   (test-system/http-port)))))
  (is 405
      (:status @(httpc/get (format "http://localhost:%s/opendap/testing/405"
                                   (test-system/http-port)))))
  (is 500
      (:status @(httpc/get (format "http://localhost:%s/opendap/testing/500"
                                   (test-system/http-port)))))
  (is 503
      (:status @(httpc/get (format "http://localhost:%s/opendap/testing/503"
                                   (test-system/http-port))))))
