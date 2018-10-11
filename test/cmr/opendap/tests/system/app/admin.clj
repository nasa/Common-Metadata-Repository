(ns ^:system cmr.opendap.tests.system.app.admin
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clojure.test :refer :all]
    [cmr.http.kit.request :as request]
    [cmr.opendap.testing.system :as test-system]
    [cmr.opendap.testing.util :as util]
    [org.httpkit.client :as httpc])
  (:import
    (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(deftest admin-routes
  (testing "ping routes ..."
    (let [response @(httpc/get (format (str "http://localhost:%s"
                                            "/service-bridge/ping")
                                       (test-system/http-port))
                               (request/add-token-header
                                {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:result "pong"}
             (util/parse-response response))))
    (let [response @(httpc/post (format (str "http://localhost:%s"
                                             "/service-bridge/ping")
                                        (test-system/http-port))
                                (request/add-token-header
                                 {} (util/get-sit-token)))]
      (is (= 200 (:status response)))
      (is (= {:result "pong"}
             (util/parse-response response))))))
