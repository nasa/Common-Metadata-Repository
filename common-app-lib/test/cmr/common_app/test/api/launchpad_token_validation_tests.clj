(ns cmr.common-app.test.api.launchpad-token-validation-tests
  "Unit tests for health checks"
  (:require
   [clojure.data.codec.base64 :as base64]
   [clojure.test :refer :all]
   [cmr.common-app.api.launchpad-token-validation :as token]))

(defn- test-matrix
  "The input-list, message-list, and expected-list are all seq of the same size.
   Items match in each seq and corrispond to the same test. 'input' is passed
   to 'tester' and then tested with '=' against 'expected'. If the test fails
   then 'message' is sent back to the test API. Parameter order allows for the
   use of partial to fix parameters expected to change the least from one test
   to the next"
  [input-list message-list expected-list tester]
  (doseq [[expected input message] (map list expected-list input-list message-list)]
    (is (= expected (tester input)) (str "Failed: " message " not " expected))))


(defn random-base64
  [len]
  (let [junk (apply str (take len (repeatedly #(char (+ (rand 26) 65)))))]
    (String. (base64/encode (.getBytes junk)) "UTF-8")))

(deftest token-tests
  ;; Security Note, these are valid looking tokens but are not and never were
  ;; active tokens. Can be generated with something like SecureRandom.hex 32
  ;; under rails
  (let [token-legacy "78221B66-EF2B-9283-19B5-295D446B065B"
        token-oauth "EDL-O84a9c44c0ad2c628300477aa2568ba39e2fa47190f5dc16f95fb24086f7"
        token-user "EDL-U84a9c44c0ad2c628300477aa2568ba39e2fa47190f5dc16f95fb24086f7"
        token-client "EDL-C84a9c44c0ad2c628300477aa2568ba39e2fa47190f5dc16f95fb24086f7"
        token-ropc "EDL-R84a9c44c0ad2c628300477aa2568ba39e2fa47190f5dc16f95fb24086f7"
        token-bearer (str "Bearer " token-user)
        token-jwt (str "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9n"
                       "aW4iLCJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiT0F1dGgiLCJ"
                       "1aWQiOiJhZG1pbiIsImNsaWVudF9pZCI6Im9YQUhISmMyLTc"
                       "wbndXb2w2T2VIUFEiLCJleHAiOjE2MTYwMTg5OTcsImlhdCI"
                       "6MTYxNjAxNTM5NywiaXNzIjoiRWFydGhkYXRhIExvZ2luIn0"
                       ".-e7GTS6PJYD1fAuCoseOj4PdV5iqd521dCM1Hc_XjqI")
        token-jwt-bearer (str "Bearer " token-jwt)
        token-jwt2 (str "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9naW4iLCJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiUmVmcmVzaCIsInVpZCI6ImNjdWFkcmFkbyIsImNsaWVudF9pZCI6Ikt4eDh5RGlRUEtjb2Mya3VKOGROOFEiLCJleHAiOjE2MTcxMjM0MDEsImlhdCI6MTYxNzExOTIwMSwiaXNzIjoiRWFydGhkYXRhIExvZ2luIn0.GEMVsVK62JWYvxYCGP1TEbcRYHKvRyWaJFQTy58uHRY")
        token-launchpad (random-base64 4096)
        all-names ["Legacy" "OAuth" "User" "Client" "ROPC" "JWT" "LaunchPad"]
        all-tokens [token-legacy token-oauth token-user token-client token-ropc token-bearer token-jwt token-jwt-bearer token-launchpad]
        test-group (partial test-matrix all-tokens all-names)]
    (testing "Is token a JWT token?"
      (test-group [false false false false false false true true false] #'token/is-jwt-token?))
    (testing "Is token a legacy token?"
      (test-group [true true true true true true false false false] #'token/is-legacy-token?))
    (testing "Is token a LaunchPad token?"
      (test-group [false false false false false false false false true] #'token/is-launchpad-token?))))
