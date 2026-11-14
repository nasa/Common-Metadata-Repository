(ns cmr.transmit.test.edl-token-validation
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.http-helper :as http-helper]
   [cmr.transmit.launchpad-user-cache :as launchpad-user-cache]
   [cmr.transmit.tokens :as tokens]
   [cmr.transmit.urs :as urs]))

(def ^:private token "eyJ0eXAiOiJKV1QifQ.eyJ1aWQiOiJ1c2VyMSJ9.c2lnbmF0dXJlLXZhbHVl")

(defn- get-token-user
  [response]
  (let [sent (atom nil)]
    (with-redefs [http-helper/request (fn [_ _ request] (reset! sent request) response)]
      [(try
         (urs/get-token-user {} (str "Bearer " token))
         (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))
       @sent])))

(deftest get-token-user-test
  (let [[result sent] (get-token-user {:status 200 :body {:username "user1" :sub "user1" :active true}})]
    (is (= {:uid "user1"} result))
    (is (= {:token token} (get-in sent [:http-options :form-params])))
    (is (= [(transmit-config/urs-username) (transmit-config/urs-password)]
           (get-in sent [:http-options :basic-auth]))))
  (is (= :unauthorized (first (get-token-user {:status 400 :body {:error "invalid_token"}}))))
  (is (= :unauthorized (first (get-token-user {:status 200 :body {:username "user1" :active false}}))))
  (is (= :dependency-failed (first (get-token-user {:status 503 :body ""})))))

(deftest jwt-validated-with-edl-when-local-verification-off-test
  (with-redefs [common-util/is-jwt-token? (constantly true)
                transmit-config/local-edl-verification (constantly false)
                launchpad-user-cache/get-edl-token-user (constantly {:uid "edl-user"})]
    (testing "local verification off"
      (is (= "edl-user" (tokens/get-user-id {} token))))))
