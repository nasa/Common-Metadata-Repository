(ns ^:integration cmr.opendap.tests.integration.rest.app
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
   [cmr.opendap.testing.system :as test-system]
   [org.httpkit.client :as httpc])
  (:import
   (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(deftest admin-routes
  (testing "health route ..."
    (let [response @(httpc/get (format "http://localhost:%s/health"
                                       (test-system/http-port)))]
      (is (= 200 (:status response)))
      (is (= {:config {:ok? true}
              :httpd {:ok? true}
              :logging {:ok? false}}
             (json/parse-string (:body response) true)))))
  (testing "protected admin route ..."
    (let [response @(httpc/get (format "http://localhost:%s/ping"
                                       (test-system/http-port)))]
      (is (= 403 (:status response)))
      (is (= "An ECHO token is required to access this resource."
             (:body response))))))

(deftest testing-routes
  (is 401
      (:status @(httpc/get (format "http://localhost:%s/testing/401"
                                   (test-system/http-port)))))
  (is 403
      (:status @(httpc/get (format "http://localhost:%s/testing/403"
                                   (test-system/http-port)))))
  (is 404
      (:status @(httpc/get (format "http://localhost:%s/testing/404"
                                   (test-system/http-port)))))
  (is 405
      (:status @(httpc/get (format "http://localhost:%s/testing/405"
                                   (test-system/http-port)))))
  (is 500
      (:status @(httpc/get (format "http://localhost:%s/testing/500"
                                   (test-system/http-port)))))
  (is 503
      (:status @(httpc/get (format "http://localhost:%s/testing/503"

                                   (test-system/http-port))))))

(deftest ous-collection-get-without-token
  "Note that when a token is not provided, the request doesn't make it past
  the network boundaries of CMR OPeNDAP, as such this is an integration test.
  With tokens, however, it does: those tests are system tests."
  (testing "Minimal get"
    (let [collection-id "C1200187767-EDF_OPS"
          response @(httpc/get
                     (format "http://localhost:%s/opendap/ous/collection/%s"
                             (test-system/http-port)
                             collection-id))]
      (is (= 403 (:status response)))
      (is (= "An ECHO token is required to access this resource."
             (:body response))))))
