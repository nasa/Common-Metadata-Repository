(ns cmr.common.test.mime-types
  "Tests for mime-type functions."
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [are2]]))

;; Tests various times when the content type should be used when extracting mime types from headers
(deftest mime-type-from-headers-test
  (testing "accept header"
    (are2
      [headers mime-type]
      (= mime-type (mt/accept-mime-type headers))

      "extract first preferred valid mime type"
      {"accept" "text/foo, application/json"} mt/json

      "accept parameters are ignored"
      {"accept" "application/xml; q=1"} mt/xml

      "nil if no accept header"
      {"content-type" "application/xml; q=1"} nil

      "nil if no acceptable type"
      {"accept" "text/foo, application/foo"} nil

      "*/* header is ignored"
      {"accept" "*/*"} nil))

  (testing "content type header"
    (are2
      [headers mime-type]
      (= mime-type (mt/content-type-mime-type headers))
      "parameters are ignored"
      {"content-type" "application/xml; q=1"} mt/xml

      "nil if no content-type header"
      {"accept" "application/xml; q=1"} nil

      "nil if no acceptable type"
      {"content-type" "text/html2, application/foo"} nil)))

(deftest convert-format-extension-to-mime-type
  (testing "valid extensions"
    (is (= mt/json (mt/path->mime-type "granules.json")))
    (is (= mt/xml (mt/path->mime-type "granules.xml")))
    (is (= mt/echo10 (mt/path->mime-type "granules.echo10")))
    (is (= mt/iso-smap (mt/path->mime-type "granules.iso-smap")))
    (is (= mt/iso-smap (mt/path->mime-type "granules.iso_smap")))
    (is (= mt/iso19115 (mt/path->mime-type "granules.iso")))
    (is (= mt/iso19115 (mt/path->mime-type "granules.iso19115")))
    (is (= mt/dif (mt/path->mime-type "granules.dif")))
    (is (= mt/csv (mt/path->mime-type "granules.csv")))
    (is (= mt/kml (mt/path->mime-type "granules.kml")))
    (is (= mt/html (mt/path->mime-type "granules.html")))
    (is (= mt/opendata (mt/path->mime-type "granules.opendata"))))
  (testing "UMM JSON with version"
    (is (= (str mt/umm-json ";version=1.3")
           (mt/path->mime-type "granules.umm_json_v1_3")))
    (is (= (str mt/umm-json ";version=99.88")
           (mt/path->mime-type "granules.umm_json_v99_88")))
    (is (= (str mt/umm-json ";version=1")
           (mt/path->mime-type "granules.umm_json_v1")))
    (is (= (str mt/umm-json ";version=1.3.4.5.66")
           (mt/path->mime-type "granules.umm_json_v1_3_4_5_66")))
    (is (= nil
           (mt/path->mime-type "granules.umm_j1son_v1_3_4_5_66")))
    (is (= nil
           (mt/path->mime-type "granules.umm_json_v1_3F_4_5_66")))
    (testing "Should be considered valid as a mime type"
      ;; No exception should be thrown.
      (mt/path->mime-type "granules.umm_json_v99_88"
                          #{mt/umm-json})))

  (testing "No extension"
    (are [uri]
      (= nil (mt/path->mime-type uri))
      "granulesjson"
      "  "
      ""))

  (testing "invalid extensions"
    (are [extension uri]
      (= nil (mt/path->mime-type uri))
      "granules.text"
      "granules.json.2"
      "granules.json/json"
      "granules.j%25son")))

(deftest test-version-of
  (is (= "1.0" (mt/version-of "application/json;version=1.0")))
  (is (= "1.0" (mt/version-of "application/json; version=1.0")))
  (is (= "1.0" (mt/version-of "application/json;charset=utf-8;version=1.0")))
  (is (= nil   (mt/version-of "application/json; not-the-version=1.0")))
  (is (= nil   (mt/version-of "application/version=1.0"))))

(deftest test-keep-version
  (is (= "application/foo" (mt/keep-version "application/foo")))
  (is (= "application/foo" (mt/keep-version "application/foo;bar=bat")))
  (is (= "application/foo;version=1.0" (mt/keep-version "application/foo; version=1.0")))
  (is (= "application/foo;version=1.0" (mt/keep-version "application/foo; charset=utf-8; version=1.0"))))

(deftest test-base-mime-type
  (is (= "application/foo" (mt/base-mime-type-of "application/foo;bar=bat")))
  (is (= nil (mt/base-mime-type-of nil))))

(deftest test-format->mime-type
  (is (= "application/json" (mt/format->mime-type :json)))
  (is (= "application/vnd.nasa.cmr.umm+json" (mt/format->mime-type :umm-json)))
  (is (= "application/vnd.nasa.cmr.umm+json;version=16.1.3" (mt/format->mime-type
                                                              {:format :umm-json
                                                               :version "16.1.3"}))))
