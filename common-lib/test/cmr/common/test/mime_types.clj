(ns cmr.common.test.mime-types
  "Tests for mime-type functions."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.common.mime-types :as mt]))

;; Tests various times when the content type should be used when extracting mime types from headers
(deftest mime-type-from-headers-test
  (testing "accept header"
    (are2
      [headers mime-type]
      (= mime-type (mt/accept-mime-type headers))

          "extract first preferred valid mime type"
          {"accept" "text/html, application/json"} mt/json

          "accept parameters are ignored"
          {"accept" "application/xml; q=1"} mt/xml

          "nil if no accept header"
          {"content-type" "application/xml; q=1"} nil

          "nil if no acceptable type"
          {"accept" "text/html, application/foo"} nil

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
          {"content-type" "text/html, application/foo"} nil)))



(deftest convert-format-extension-to-mime-type
  (testing "valid extensions"
    (is (= mt/json (mt/path-w-extension->mime-type "granules.json")))
    (is (= mt/xml (mt/path-w-extension->mime-type "granules.xml")))
    (is (= mt/echo10 (mt/path-w-extension->mime-type "granules.echo10")))
    (is (= mt/iso-smap (mt/path-w-extension->mime-type "granules.iso-smap")))
    (is (= mt/iso-smap (mt/path-w-extension->mime-type "granules.iso_smap")))
    (is (= mt/iso (mt/path-w-extension->mime-type "granules.iso")))
    (is (= mt/iso (mt/path-w-extension->mime-type "granules.iso19115")))
    (is (= mt/dif (mt/path-w-extension->mime-type "granules.dif")))
    (is (= mt/csv (mt/path-w-extension->mime-type "granules.csv")))
    (is (= mt/kml (mt/path-w-extension->mime-type "granules.kml")))
    (is (= mt/opendata (mt/path-w-extension->mime-type "granules.opendata"))))
  (testing "invalid extensions"
    (are [uri]
         (= nil (mt/path-w-extension->mime-type uri))
         "granules.text"
         "granules.json.2"
         "granulesjson"
         "  "
         ""
         "granules.json/json"
         "granules.j%25son")))
