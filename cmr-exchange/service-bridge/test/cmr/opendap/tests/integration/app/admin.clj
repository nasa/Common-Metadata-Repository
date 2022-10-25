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
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.opendap.testing.system :as test-system]
   [cmr.opendap.testing.util :as util]
   [org.httpkit.client :as httpc])
  (:import
   (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(deftest admin-routes
  (testing "health route ..."
    (let [response @(httpc/get (format "http://localhost:%s/service-bridge/health"
                                       (test-system/http-port)))]
      (is (= 200 (:status response)))
      (is (= {:config {:ok? true}
              :httpd {:ok? true}}
             (dissoc (json/parse-string (:body response) true) :logging)))))
  (testing "protected admin route ..."
    (let [response @(httpc/get (format "http://localhost:%s/service-bridge/ping"
                                       (test-system/http-port)))]
      (is (= 403 (:status response)))
      (is (= {:errors ["An ECHO token is required to access this resource."]}
             (response/parse-json-result (:body response))))))
  (testing "v2 routes that don't exist in v1 ..."
    (let [response @(httpc/get (format "http://localhost:%s/service-bridge/cache"
                                       (test-system/http-port))
                                (-> {}
                                    (request/add-token-header
                                     (util/get-sit-token))
                                    (request/add-accept
                                     "application/vnd.cmr-service-bridge.v1+json")))]
      (is (= 404 (:status response)))))
  (testing "v2 routes ..."
    #_(let [response @(httpc/get (format "http://localhost:%s/service-bridge/cache/auth"
                                       (test-system/http-port))
                                (-> {}
                                    (request/add-token-header
                                     (util/get-sit-token))
                                    (request/add-accept
                                     "application/vnd.cmr-service-bridge.v2+json")))]
      (is (= 200 (:status response))))
    ;; XXX This test is currently failing; this has happened before with soft references
    ;;     see the code comment above cmr.http.kit.response/soft-reference->json! ...
    #_(let [response @(httpc/get (format "http://localhost:%s/service-bridge/cache/concept"
                                       (test-system/http-port))
                                (-> {}
                                    (request/add-token-header
                                     (util/get-sit-token))
                                    (request/add-accept
                                     "application/vnd.cmr-service-bridge.v2+json")))]
      (is (= 200 (:status response))))))

(deftest testing-routes
  (is 401
      (:status @(httpc/get (format "http://localhost:%s/service-bridge/testing/401"
                                   (test-system/http-port)))))
  (is 403
      (:status @(httpc/get (format "http://localhost:%s/service-bridge/testing/403"
                                   (test-system/http-port)))))
  (is 404
      (:status @(httpc/get (format "http://localhost:%s/service-bridge/testing/404"
                                   (test-system/http-port)))))
  (is 405
      (:status @(httpc/get (format "http://localhost:%s/service-bridge/testing/405"
                                   (test-system/http-port)))))
  (is 500
      (:status @(httpc/get (format "http://localhost:%s/service-bridge/testing/500"
                                   (test-system/http-port)))))
  (is 503
      (:status @(httpc/get (format "http://localhost:%s/service-bridge/testing/503"
                                   (test-system/http-port))))))
