(ns ^:integration cmr.opendap.tests.integration.app.root
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

(deftest root-route
  (testing "root route ..."
    (let [response @(httpc/get (format "http://localhost:%s/service-bridge"
                                       (test-system/http-port)))]
      (is (= 200 (:status response))))
    (let [response @(httpc/head (format "http://localhost:%s/service-bridge"
                                        (test-system/http-port)))]
      (is (= 200 (:status response)))))
  (testing "root route with trailing slash ..."
    (let [response @(httpc/get (format "http://localhost:%s/service-bridge/"
                                       (test-system/http-port)))]
      (is (= 200 (:status response))))
    (let [response @(httpc/head (format "http://localhost:%s/service-bridge/"
                                        (test-system/http-port)))]
      (is (= 200 (:status response))))))
