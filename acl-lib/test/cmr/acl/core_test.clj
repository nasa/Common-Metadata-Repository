(ns cmr.acl.core-test
  "test functions in acl core"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.acl.core :as acl_core]))

(deftest non-empty-string
  (testing "string is empty"
    (is (= (acl_core/non-empty-string "") nil)))
  (testing "string is nonempty"
    (is (= (acl_core/non-empty-string "!") "!"))))

(deftest get-token-test
  (testing "header token exist, param token does not exist"
    (let [params {}
          headers {"authorization" "header-token"}]
      (is (= (acl_core/get-token params headers) "header-token"))))
  (testing "header token not exit, param token exist"
    (let [params {:token "params-token"}
          headers {}]
      (is (= (acl_core/get-token params headers) "params-token"))))
  (testing "header token, param token exist and equal each other"
    (let [params {:token "same-token"}
          headers {"authorization" "same-token"}]
      (is (= (acl_core/get-token params headers) "same-token"))))
  (testing "header token, param token exist and do not equal each other"
    (let [params {:token "params-token"}
          headers {"authorization" "header-token"}]
      (is (thrown? Exception (acl_core/get-token params headers))))))


(deftest get-client-id-test
  (testing "user agent is mozilla"
    (let [headers {"user-agent" "Mozilla."}
          fun #'cmr.acl.core/get-client-id]
      (is (= (fun headers) acl_core/BROWSER_CLIENT_ID))))
  (testing "user agent is curl"
    (let [headers {"user-agent" "curl."}
          fun #'cmr.acl.core/get-client-id]
      (is (= (fun headers) acl_core/CURL_CLIENT_ID))))
  (testing "user agent is opera"
    (let [headers {"user-agent" "Opera."}
          fun #'cmr.acl.core/get-client-id]
      (is (= (fun headers) acl_core/BROWSER_CLIENT_ID))))
  (testing "client id does not exist"
    (let [headers {}
          fun #'cmr.acl.core/get-client-id]
      (is (= (fun headers) acl_core/UNKNOWN_CLIENT_ID))))
  (testing "unknown client id"
    (let [headers {"user-agent" "Other"}
          fun #'cmr.acl.core/get-client-id]
      (is (= (fun headers) acl_core/UNKNOWN_CLIENT_ID)))))

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
    (with-redefs [cmr.acl.core/request-sids (fn [context] [:guest])]
      (is (= (acl_core/context->sids context) [:guest]))))
  (testing "get sids from context"
    (let [context {:sids ["guest"]}]
    (is (= (acl_core/context->sids context) ["guest"]))))))

(deftest get-permitting-acls-test
  (testing "Exception getting acls"
    (with-redefs [cmr.transmit.access-control/acl-type->acl-key (fn [object-identity-type] (throw (Exception. "Exception to break test")))]
      (is (nil? (acl_core/get-permitting-acls nil nil nil nil)))))
  (testing "Exception getting acls with 401 message"
    (with-redefs [cmr.transmit.access-control/acl-type->acl-key (fn [object-identity-type] (throw (Exception. "Exception to break test with status 401")))]
      (is (thrown? Exception (acl_core/get-permitting-acls nil nil nil nil))))))