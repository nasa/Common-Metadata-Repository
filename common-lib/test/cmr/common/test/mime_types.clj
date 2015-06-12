(ns cmr.common.test.mime-types
  "Tests for mime-type functions."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.mime-types :as mt]))

;; Tests various times when the content type should be used when extracting mime types from headers
(deftest mime-type-from-headers-test
  (testing "extract first preferred valid mime type"
    (is (= mt/json
           (mt/mime-type-from-headers
             {"accept" "text/html, application/json;q=9"}))))

  (testing "accept header used if available"
    (is (= mt/iso-smap
           (mt/mime-type-from-headers
             {"accept" "application/iso:smap+xml"
              "content-type" "application/xml"})))

    (testing "accept parameters are ignored"
      (is (= mt/xml
             (mt/mime-type-from-headers
               {"accept" "application/xml; q=1"})))))

  (testing "content type used if accept not set"
    (is (= mt/xml
           (mt/mime-type-from-headers
             {"content-type" "application/xml"})))

    (testing "accept parameters are ignored"
      (is (= mt/xml
             (mt/mime-type-from-headers
               {"content-type" "application/xml; q=1"})))))

  (testing "*/* in accept header is ignored"
    (testing "use content type if not provided"
      (is (= mt/xml
             (mt/mime-type-from-headers
               {"accept" "*/*"
                "content-type" "application/xml"}))))
    (testing "nil returned otherwise"
      (is (nil? (mt/mime-type-from-headers {"accept" "*/*"}))))

    (testing "accept parameters are ignored"
      (is (nil? (mt/mime-type-from-headers {"accept" "*/*; q=1"}))))))


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
