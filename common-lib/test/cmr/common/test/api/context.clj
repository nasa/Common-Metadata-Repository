(ns cmr.common.test.api.context
  "This tests capabilities of the API context utilities."
  (:require
   [clojure.test :refer :all]
   [cmr.common.api.context :as context]))

(deftest request-context-test
  (let [system {:foo "bar"}
        request-id "request-test"]
    (is (= {:system {:foo "bar"} :request "request-test"} (context/request-context system request-id)))))

(deftest context->request-id-test
  (let [context {:request {:request-id "request-test"}}]
    (is (= "request-test" (context/context->request-id context)))))

(deftest context->http-headers-test
  (let [context {:request {:request-id "request-test"}}]
    (is (= {"cmr-request=id" "request-test"} (context/context->http-headers context)))))

(deftest context->user-id-test
  (let [context-1 {:request {:request-id "request-test"} :user-id "test-user"}
        context-2 {:request {:request-id "request-test"} :token "test-token" :user-id "test-user"}
        msg "No token test"]
    (is (= "test-user" (context/context->user-id context-2)))))