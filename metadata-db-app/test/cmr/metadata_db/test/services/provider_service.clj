(ns cmr.metadata-db.test.services.provider-service
  "Contains unit tests for service layer methods and associated utility methods."
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [cmr.metadata-db.services.provider-service :as ps]
            [cmr.metadata-db.services.concept-service :as cs]
            [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.messages :as messages]
            [cmr.common.dev.util :as du])
  (import clojure.lang.ExceptionInfo))

(defn assert-exception-info-contains-errors
  "Asserts the ExceptionInfo exception contains a "
  [e errors]
  (is (= {:type :bad-request :errors errors} (ex-data e))))

(defmacro assert-exception-thrown-with-errors
  [errors & body]
  `(try
     (do
       ~@body)
     (is false "No exception was thrown")
     (catch ExceptionInfo e#
       (assert-exception-info-contains-errors e# ~errors))))


(deftest validate-provider-test
  (testing "valid provider"
    (ps/validate-provider {:provider-id "PROV1" :cmr-only false}))
  (testing "invalid provider-ids"
    (testing "empty provider-id"
      (assert-exception-thrown-with-errors
        [(messages/provider-id-empty)]
        (ps/validate-provider {:provider-id "" :cmr-only false})))
    (testing "nil provider-id"
      (assert-exception-thrown-with-errors
        [(messages/provider-id-empty)]
        (ps/validate-provider {:provider-id nil :cmr-only false})))
    (testing "provider-id too long"
      (assert-exception-thrown-with-errors
        [(messages/provider-id-too-long "a2345678901")]
        (ps/validate-provider {:provider-id "a2345678901" :cmr-only false})))
    (testing "invalid character"
      (assert-exception-thrown-with-errors
        [(messages/invalid-provider-id "ab:123")]
        (ps/validate-provider {:provider-id "ab:123" :cmr-only false}))))
  (testing "invalid cmr-only"
    (testing "not provided"
      (assert-exception-thrown-with-errors
        ["Cmr Only is required."]
        (ps/validate-provider {:provider-id "PROV1" :cmr-only nil})))
    (testing "not boolean"
      (assert-exception-thrown-with-errors
        ["Cmr Only must be either true or false but was [\"true\"]"]
        (ps/validate-provider {:provider-id "PROV1" :cmr-only "true"})))))
