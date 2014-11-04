(ns cmr.metadata-db.test.services.provider-service
  "Contains unit tests for service layer methods and associated utility methods."
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.services.concept-service :as cs]
            [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.messages :as messages]
            [cmr.common.dev.util :as du])
  (import clojure.lang.ExceptionInfo))

(deftest validate-provider-id-test
  (testing "valid provider-id"
    (let [provider-id "PROV_1"]
      (is (nil? (util/validate-provider-id provider-id)))))
  (testing "empty provider-id"
    (let [provider-id ""]
      (is (thrown-with-msg? ExceptionInfo (du/message->regex (messages/provider-id-empty provider-id))
                            (util/validate-provider-id provider-id)))))
  (testing "nil provider-id"
    (let [provider-id nil]
      (is (thrown-with-msg? ExceptionInfo (du/message->regex (messages/provider-id-empty provider-id))
                            (util/validate-provider-id provider-id)))))
  (testing "provider-id too long"
    (let [provider-id "ab123456789"]
      (is (thrown-with-msg? ExceptionInfo (du/message->regex (messages/provider-id-too-long provider-id))
                            (util/validate-provider-id provider-id)))))
  (testing "invalid character"
    (let [provider-id "ab:123"]
      (is (thrown-with-msg? ExceptionInfo (du/message->regex (messages/invalid-provider-id provider-id))
                            (util/validate-provider-id provider-id))))))