(ns cmr.acl.test.core-test
  "test functions in acl core"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.acl.core :as acl_core]
    [cmr.common.util :refer [are3]]
    [cmr.transmit.access-control :as access-control]))

(deftest non-empty-string-test
  (testing "non-empty-string test"
    (are3 [input-str expected]
          (is (= (acl_core/non-empty-string input-str) expected))

          "string is empty"
          ;; input-str expected
          "" nil

          "string is nonempty"
          "!" "!")))

(deftest get-token-test
  (testing "get token test"
    (are3 [params headers expected-token]
          (is (= (acl_core/get-token params headers) expected-token))

          "header token exist, param token does not exist"
          ;; params headers expected-token values
          {} {"authorization" "header-token"} "header-token"

          "header token not exist, param token exist"
          {:token "params-token"} {} "params-token"

          "header token, param token exist and equal each other"
          {:token "same-token"} {"authorization" "same-token"} "same-token"))
  (testing "header token, param token exist and do not equal each other"
    (let [params {:token "params-token"}
          headers {"authorization" "header-token"}]
      (is (thrown? Exception (acl_core/get-token params headers))))))

(deftest get-client-id-test
  (testing "get client id test"
    (are3 [headers expected-client-id]
          (let [fun #'cmr.acl.core/get-client-id]
            (is (= (fun headers) expected-client-id)))

          "user agent is mozilla"
          {"user-agent" "Mozilla."} acl_core/BROWSER_CLIENT_ID

          "user agent is curl"
          {"user-agent" "curl."} acl_core/CURL_CLIENT_ID

          "user agent is opera"
          {"user-agent" "Opera."} acl_core/BROWSER_CLIENT_ID

          "client id does not exist"
          {} acl_core/UNKNOWN_CLIENT_ID

          "unknown client id"
          {"user-agent" "Other"} acl_core/UNKNOWN_CLIENT_ID)))

(deftest add-authentication-to-context-test
  (let [context {}
        params {:token "params-token"}
        headers {"user-agent" "curl."}
        fun #'cmr.acl.core/add-authentication-to-context
        expected-context {:token "params-token" :client-id acl_core/CURL_CLIENT_ID}]
    (println (fun context params headers))
    (is (= (fun context params headers) expected-context))))

(deftest context->sids-test
  (testing "get sids from requesting it"
    (let [context {:keys ["params-token"]}]
    (with-redefs [cmr.acl.core/request-sids (fn [_context] [:guest])]
      (is (= (acl_core/context->sids context) [:guest]))))
  (testing "get sids from context"
    (let [context {:sids [:guest]}]
    (is (= (acl_core/context->sids context) [:guest]))))))

(deftest get-permitting-acls-test
  (testing "Exception getting acls"
    (with-redefs [access-control/acl-type->acl-key (fn [_object-identity-type] (throw (Exception. "Exception to break test")))]
      (is (nil? (acl_core/get-permitting-acls nil nil nil nil)))))
  (testing "Exception getting acls with 401 message"
    (with-redefs [access-control/acl-type->acl-key (fn [_object-identity-type] (throw (Exception. "Exception to break test with status 401")))]
      (is (thrown? Exception (acl_core/get-permitting-acls nil nil nil nil))))))
