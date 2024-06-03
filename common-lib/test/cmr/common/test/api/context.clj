(ns cmr.common.test.api.context
  "This tests capabilities of the API context utilities."
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.util :as util]
   [cmr.common.api.context :as context])
  (:import
   [clojure.lang ExceptionInfo]))

(deftest request-context-test
  (testing "Normal usage with supplied request-id"
    (let [system {:foo "bar"}
          request-id "request-test"]
      (is (= {:system {:foo "bar"} :request {:request-id "request-test"}}
             (context/request-context system request-id))))))

(deftest context->request-id-test
  (testing "Normal usage, pulling request-id from the context"
    (let [context {:request {:request-id "request-test"}}]
      (is (= "request-test" (context/context->request-id context))))))

(deftest context->http-headers-test
  (testing "Normal usage, creating HTTP header map"
    (let [context {:request {:request-id "request-test"}}]
      (is (= {"cmr-request-id" "request-test"} (context/context->http-headers context))))))

(deftest context->user-id-test
  (testing "Normal usage, pulling user-id from the context"
    (let [context-temp {:request {:request-id "request-test"} :token "test-token"}
          context (util/lazy-assoc context-temp :user-id "test-user")]
      (is (= "test-user" (context/context->user-id context)))))
  (testing "Testing getting user id without token"
    (let [context (util/lazy-assoc {} :user-id "testing")]
      (is (thrown? ExceptionInfo (context/context->user-id context))))))
