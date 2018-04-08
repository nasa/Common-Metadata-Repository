(ns ^:integration cmr.opendap.tests.integration.rest.app
  "Note: this namespace is exclusively for integration tests; all tests defined
  here will use one or more integration test fixtures.

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
    (let [result @(httpc/get (format "http://localhost:%s/health"
                                     (test-system/http-port)))]
      (is (= 200 (:status result)))
      (is (= {:config {:ok? true}
              :httpd {:ok? true}
              :logging {:ok? false}}
             (json/parse-string (:body result) true))))))

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
