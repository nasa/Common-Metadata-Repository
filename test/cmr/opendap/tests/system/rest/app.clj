(ns ^:system cmr.opendap.tests.system.rest.app
  "Note: this namespace is exclusively for system tests; all tests defined
  here will use one or more system test fixtures.

  Definition used for system tests:
  * https://en.wikipedia.org/wiki/Software_testing#System_testing"
  (:require
    [clj-http.client :as httpc]
    [clojure.test :refer :all]
    [cmr.opendap.testing.system :as test-system])
  (:import
    (clojure.lang ExceptionInfo)))

(use-fixtures :once test-system/with-system)

(deftest admin-routes
  ;; XXX Move this to a system test and read creds from filesystem
  ; (testing "ping routes ..."
  ;   (let [result (httpc/get (format "http://localhost:%s/ping"
  ;                                   (test-system/http-port))
  ;                           {:as :json})]
  ;     (is (= 200 (:status result)))
  ;     (is (= "pong" (get-in result [:body :result]))))
  ;   (let [result (httpc/post (format "http://localhost:%s/ping"
  ;                                    (test-system/http-port))
  ;                            {:as :json})]
  ;     (is (= 200 (:status result)))
  ;     (is (= "pong" (get-in result [:body :result])))))
  )
