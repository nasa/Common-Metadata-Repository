(ns cmr.common-app.test.launchpad-token-validation-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common-app.config :as config]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.tokens :as tokens]
   [cmr.acl.core :as acl]
   [cmr.common.util :as util]))

(defn mock-context [token]
  {:token token})

(defn mock-get-jwt-claims [token]
  (case token
    "JWT-L5" {:uid "user-l5" :assurance_level 5}
    "JWT-L4" {:uid "user-l4" :assurance_level 4}
    "JWT-L3" {:uid "user-l3" :assurance_level 3}
    "JWT-L2" {:uid "user-l2" :assurance_level 2}
    "JWT-NO-LEVEL" {:uid "user-no-level"}
    nil))

(deftest validate-write-token-level-5-success-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly false)
                config/required-assurance-level (constantly 4)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "Level 5 JWT passes validation without ACL check"
      (is (nil? (lt-validation/validate-write-token
                 (mock-context "JWT-L5")
                 "PROV1"))))))

(deftest validate-write-token-level-4-success-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly false)
                config/required-assurance-level (constantly 4)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")
                ;; Mock ACL check to pass
                acl/verify-non-nasa-draft-permission (fn [_ _ _ _] nil)]

    (testing "Level 4 JWT passes when NON_NASA_DRAFT_USER ACL check succeeds"
      (is (nil? (lt-validation/validate-write-token
                 (mock-context "JWT-L4")
                 "PROV1"))))))

(deftest validate-write-token-level-4-no-provider-id-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly false)
                config/required-assurance-level (constantly 4)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "Level 4 JWT without provider-id throws permission error"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"You do not have permission to perform that action"
                            (lt-validation/validate-write-token
                             (mock-context "JWT-L4")))))))

(deftest validate-write-token-level-3-rejected-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly false)
                config/required-assurance-level (constantly 4)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "Level 3 JWT is rejected (below minimum assurance level)"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lt-validation/validate-write-token
                    (mock-context "JWT-L3")
                    "PROV1"))))))

(deftest validate-write-token-level-2-rejected-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly false)
                config/required-assurance-level (constantly 4)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "Level 2 JWT is rejected (below minimum assurance level)"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lt-validation/validate-write-token
                    (mock-context "JWT-L2")
                    "PROV1"))))))

(deftest validate-write-token-missing-assurance-level-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly false)
                config/required-assurance-level (constantly 4)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "JWT without assurance_level claim is rejected"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lt-validation/validate-write-token
                    (mock-context "JWT-NO-LEVEL")
                    "PROV1"))))))

(deftest validate-write-token-invalid-jwt-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly false)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "Invalid JWT (fails decryption) is rejected"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lt-validation/validate-write-token
                    (mock-context "JWT-INVALID")
                    "PROV1"))))))

(deftest validate-write-token-saml-token-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (constantly false)
                util/is-launchpad-token? (fn [token] (= token "SAML-TOKEN"))
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly true)
                config/enable-launchpad-saml-authentication (constantly true)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "Launchpad SAML token passes when enabled"
      (is (nil? (lt-validation/validate-write-token
                 (mock-context "SAML-TOKEN")
                 "PROV1"))))))

(deftest validate-write-token-jwt-disabled-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly false)
                config/enable-launchpad-saml-authentication (constantly false)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "JWT tokens rejected when feature toggle disabled"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lt-validation/validate-write-token
                    (mock-context "JWT-L5")
                    "PROV1"))))))

(deftest validate-write-token-saml-disabled-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (constantly false)
                util/is-launchpad-token? (fn [token] (= token "SAML-TOKEN"))
                config/launchpad-token-enforced (constantly true)
                config/enable-idfed-jwt-authentication (constantly false)
                config/enable-launchpad-saml-authentication (constantly false)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "SAML tokens rejected when feature toggle disabled"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lt-validation/validate-write-token
                    (mock-context "SAML-TOKEN")
                    "PROV1"))))))

(deftest validate-write-token-enforcement-disabled-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                util/is-jwt-token? (fn [token] (re-find #"^JWT-" token))
                util/is-launchpad-token? (constantly false)
                config/launchpad-token-enforced (constantly false)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "Token validation skipped when enforcement disabled"
      ;; Should not validate or throw when enforcement is off
      (is (nil? (lt-validation/validate-write-token
                 (mock-context "JWT-L3")
                 "PROV1"))))))

(deftest validate-write-token-system-token-test
  (with-redefs [tokens/get-jwt-claims mock-get-jwt-claims
                config/launchpad-token-enforced (constantly true)
                transmit-config/echo-system-token (constantly "SYSTEM-TOKEN")]

    (testing "System token always passes"
      (is (nil? (lt-validation/validate-write-token
                 (mock-context "SYSTEM-TOKEN")
                 "PROV1"))))))
