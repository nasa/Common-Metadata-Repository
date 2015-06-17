(ns cmr.metadata-db.test.services.provider-validation
  "Contains unit tests for service layer methods and associated utility methods."
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [cmr.metadata-db.services.provider-validation :as pv]
            [cmr.metadata-db.services.messages :as messages]
            [cmr.common.util :as u]
            [cmr.common.test.test-util :as tu]))

(def long-provider-id
  "A provider id with 255 characters, maximum allowed."
  (str (apply str (repeat 10 "ABCDEFGHIJKLMNOPQRSTUVWXY")) "12345"))

(def too-long-provider-id
  "A provider id with 255 characters, maximum allowed."
  (str (apply str (repeat 10 "ABCDEFGHIJKLMNOPQRSTUVWXY")) "123456"))

(deftest validate-provider-test
  (testing "valid provider"
    (are [provider-id short-name cmr-only small]
         (nil? (pv/validate-provider {:provider-id provider-id
                                      :short-name short-name
                                      :cmr-only false
                                      :small false}))
         "PROV1" "PROV1" false false
         long-provider-id "PROV2" true false))
  (testing "invalid providers"
    (u/are2 [attrs error]
            (tu/assert-exception-thrown-with-errors
              :bad-request
              [error]
              (pv/validate-provider (merge {:provider-id "PROV1"
                                            :short-name "PROV1"
                                            :cmr-only false
                                            :small false}
                                           attrs)))

            "empty provider-id"
            {:provider-id ""}
            "Provider Id cannot be empty"

            "nil provider-id"
            {:provider-id nil}
            "Provider Id cannot be empty"

            "provider-id too long"
            {:provider-id too-long-provider-id}
            (format "Provider Id [%s] exceeds 255 characters" too-long-provider-id)

            "provider-id invalid character"
            {:provider-id "ab:123"}
            "Provider Id [ab:123] is invalid"

            "empty short-name"
            {:short-name ""}
            "Short Name cannot be empty"

            "nil short-name"
            {:short-name nil}
            "Short Name cannot be empty"

            "short-name too long"
            {:short-name "a2345678901"}
            "Short Name [a2345678901] exceeds 10 characters"

            "short-name invalid character"
            {:short-name "ab:123"}
            "Short Name [ab:123] is invalid"

            "cmr-only not provided"
            {:cmr-only nil}
            "Cmr Only is required."

            "cmr-only not boolean"
            {:cmr-only "true"}
            "Cmr Only must be either true or false but was [\"true\"]"

            "small not provided"
            {:small nil}
            "Small is required."

            "small not boolean"
            {:small "true"}
            "Small must be either true or false but was [\"true\"]")))
