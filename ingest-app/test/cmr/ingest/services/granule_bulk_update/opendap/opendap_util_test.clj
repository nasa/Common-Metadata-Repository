(ns cmr.ingest.services.granule-bulk-update.opendap.opendap-util-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util :as opendap-util]))

(deftest validate-url-test
  (testing "validate url"
    (is (= {:on-prem ["http://example.com/foo"]
            :cloud ["https://opendap.uat.earthdata.nasa.gov/foo"]}
           (opendap-util/validate-url
            "http://example.com/foo, https://opendap.uat.earthdata.nasa.gov/foo"))))

  (testing "validate url error scenarios"
    (are3 [url-value re]
      (is (thrown-with-msg?
           Exception re (opendap-util/validate-url url-value)))

      "more than 2 urls provided"
      "http://example.com/foo,http://example.com/bar,http://example.com/baz"
      #"Invalid URL value, no more than two urls can be provided:"

      "more than one on-prem url provided"
      "http://example.com/foo,http://example.com/bar"
      #"Invalid URL value, no more than one on-prem OPeNDAP url can be provided:"

      "more than one Hyrax-in-the-cloud url provided"
      "https://opendap.earthdata.nasa.gov/foo,https://opendap.uat.earthdata.nasa.gov/foo"
      #"Invalid URL value, no more than one Hyrax-in-the-cloud OPeNDAP url can be provided:")))

(deftest is-opendap-test
  (are3 [type-string result] (is (= result (opendap-util/is-opendap? type-string)))
    "lower case" "opendap" true
    "upper case" "OPENDAP" true
    "Mixed case" "OPeNDAP" true
    "GET DATA" "GET DATA : OPENDAP DATA" true
    "USE SERVICE API" "USE SERVICE API : OPENDAP DATA" true
    "Other type" "README" false
    "empty string" "" false
    "non-string" 0 false
    "keyword" :opendap false
    "nil case" nil false))
