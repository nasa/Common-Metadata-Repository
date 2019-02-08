(ns cmr.metadata-db.test.services.provider-validation
  "Contains unit tests for service layer methods and associated utility methods."
  (:require
   [clojure.test :refer :all]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cmr.common.test.test-util :as tu]
   [cmr.common.util :as u]
   [cmr.metadata-db.services.messages :as messages]
   [cmr.metadata-db.services.provider-validation :as pv]))

(def long-short-name
  "A provider short name with 128 characters, maximum allowed."
  (str (apply str (repeat 6 "ABCDEFGHIJKLMNOPQRST")) "12345678"))

(def too-long-short-name
  "A provider short name with 129 characters."
  (str long-short-name "1"))

(deftest validate-provider-test
  (testing "valid provider"
    (are [provider-id short-name cmr-only small]
         (nil? (pv/validate-provider {:provider-id provider-id
                                      :short-name short-name
                                      :cmr-only false
                                      :small false}))
         "PROV1" "PROV1" false false
         "PROV1" "Dept of Commerce/NOAA/Weather Balloons" false false
         "P123456789" long-short-name true false))
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
            "Provider Id cannot be blank"

            "nil provider-id"
            {:provider-id nil}
            "Provider Id cannot be blank"

            "provider-id too long"
            {:provider-id "A2345678901"}
            "Provider Id [A2345678901] exceeds 10 characters"

            "provider-id invalid character"
            {:provider-id "ab:123"}
            "Provider Id [ab:123] is invalid"

            "empty short-name"
            {:short-name "   "}
            "Short Name cannot be blank"

            "nil short-name"
            {:short-name nil}
            "Short Name cannot be blank"

            "short-name too long"
            {:short-name too-long-short-name}
            (format "Short Name [%s] exceeds 128 characters" too-long-short-name)

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
