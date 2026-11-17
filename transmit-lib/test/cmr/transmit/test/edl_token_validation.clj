(ns cmr.transmit.test.edl-token-validation
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.http-helper :as http-helper]
   [cmr.transmit.launchpad-user-cache :as launchpad-user-cache]
   [cmr.transmit.tokens :as tokens]
   [cmr.transmit.urs :as urs]))

;; Sample token, safe for use in tests: decodes to {"typ":"JWT"}.{"uid":"user1"}.signature-value
(def ^:private token "eyJ0eXAiOiJKV1QifQ.eyJ1aWQiOiJ1c2VyMSJ9.c2lnbmF0dXJlLXZhbHVl")

(defn- get-token-user
  [response]
  (let [sent (atom nil)]
    (with-redefs [http-helper/request (fn [_ _ request]
                                        (reset! sent request)
                                        (if (instance? Exception response) (throw response) response))]
      [(try
         (urs/get-token-user {} (str "Bearer " token))
         (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))
       @sent])))

(deftest get-token-user-test
  (let [[result sent] (get-token-user {:status 200 :body {:username "user1" :sub "user1" :active true :exp 100}})]
    (is (= {:uid "user1" :exp 100} result))
    (is (= {:token token} (get-in sent [:http-options :form-params])))
    (is (= [(transmit-config/urs-username) (transmit-config/urs-password)]
           (get-in sent [:http-options :basic-auth])))
    (is (= 10000 (get-in sent [:http-options :socket-timeout]))))
  (is (= :unauthorized (first (get-token-user {:status 400 :body {:error "invalid_token"}}))))
  (is (= :unauthorized (first (get-token-user {:status 200 :body {:username "user1" :active false}}))))
  (testing "EDL problems are dependency failures, not bad tokens"
    (is (= :dependency-failed (first (get-token-user {:status 503 :body ""}))))
    (is (= :dependency-failed (first (get-token-user {:status 200 :body {:username "user1"}}))))
    (is (= :dependency-failed (first (get-token-user (java.net.SocketTimeoutException. "Read timed out"))))))
  (is (= :internal-server-error (first (get-token-user {:status 401 :body "HTTP Basic: Access denied."})))))

(deftest jwt-validated-with-edl-when-local-verification-off-test
  (with-redefs [common-util/is-jwt-token? (constantly true)
                transmit-config/local-edl-verification (constantly false)
                launchpad-user-cache/get-edl-token-user (constantly {:uid "edl-user"})]
    (testing "local verification off"
      (is (= "edl-user" (tokens/get-user-id {} token))))))

(deftest edl-token-user-cache-test
  (let [calls (atom 0)
        ctx {:system {:caches {launchpad-user-cache/launchpad-user-cache-key
                               (launchpad-user-cache/create-launchpad-user-cache)}}}
        lookup (fn [response] (fn [_ _] (swap! calls inc) (if (instance? Exception response) (throw response) response)))
        error-of (fn [t] (try (launchpad-user-cache/get-edl-token-user ctx t) nil
                              (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))]
    (testing "a valid token is checked once, then served from the cache"
      (with-redefs [urs/get-token-user (lookup {:uid "u1"})]
        (launchpad-user-cache/get-edl-token-user ctx "t1")
        (launchpad-user-cache/get-edl-token-user ctx "t1")
        (is (= 1 @calls))))
    (testing "an entry never outlives the token's exp"
      (reset! calls 0)
      (with-redefs [urs/get-token-user (lookup {:uid "u1" :exp 1})]
        (launchpad-user-cache/get-edl-token-user ctx "t2")
        (launchpad-user-cache/get-edl-token-user ctx "t2")
        (is (= 2 @calls))))
    (testing "a rejected token is cached, rate limiting is not"
      (reset! calls 0)
      (with-redefs [urs/get-token-user (lookup (ex-info "no" {:type :unauthorized}))]
        (is (= :unauthorized (error-of "t3")))
        (is (= :unauthorized (error-of "t3")))
        (is (= 1 @calls)))
      (reset! calls 0)
      (with-redefs [urs/get-token-user (lookup (ex-info "slow down" {:type :too-many-requests}))]
        (error-of "t4")
        (error-of "t4")
        (is (= 2 @calls))))))
