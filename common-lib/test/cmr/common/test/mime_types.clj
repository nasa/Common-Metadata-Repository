(ns cmr.common.test.mime-types
  "Tests for mime-type functions."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.mime-types-helper :as mth]))

(def supported-mime-types
  "The mime-types supported by search."
  #{"*/*"
    "application/xml"
    "application/json"
    "application/echo10+xml"
    "application/dif+xml"
    "application/atom+xml"
    "application/iso19115+xml"
    "application/iso:smap+xml"
    "text/csv"
    "application/vnd.google-earth.kml+xml"
    "application/opendata+json"})

;; Tests various times when the content type should be used when extracting mime types from headers
(deftest mime-type-from-headers-test
  (testing "extract first preferred valid mime type"
    (is (= "application/json"
           (mt/mime-type-from-headers
             {"accept" "text/html, application/json;q=9"}))))

  (testing "accept header used if available"
    (is (= "application/iso:smap+xml"
           (mt/mime-type-from-headers
             {"accept" "application/iso:smap+xml"
              "content-type" "application/xml"})))

    (testing "accept parameters are ignored"
      (is (= "application/xml"
             (mt/mime-type-from-headers
               {"accept" "application/xml; q=1"})))))

  (testing "content type used if accept not set"
    (is (= "application/xml"
           (mt/mime-type-from-headers
             {"content-type" "application/xml"})))

    (testing "accept parameters are ignored"
      (is (= "application/xml"
             (mt/mime-type-from-headers
               {"content-type" "application/xml; q=1"})))))

  (testing "*/* in accept header is ignored"
    (testing "use content type if not provided"
      (is (= "application/xml"
             (mt/mime-type-from-headers
               {"accept" "*/*"
                "content-type" "application/xml"}))))
    (testing "nil returned otherwise"
      (is (nil? (mt/mime-type-from-headers {"accept" "*/*"}))))

    (testing "accept parameters are ignored"
      (is (nil? (mt/mime-type-from-headers {"accept" "*/*; q=1"}))))))


(deftest convert-format-extension-to-mime-type
  (testing "valid extensions"
    (is (= "application/json" (mt/path-w-extension->mime-type "granules.json")))
    (is (= "application/xml" (mt/path-w-extension->mime-type "granules.xml")))
    (is (= "application/echo10+xml" (mt/path-w-extension->mime-type "granules.echo10")))
    (is (= "application/iso:smap+xml" (mt/path-w-extension->mime-type "granules.iso-smap")))
    (is (= "application/iso:smap+xml" (mt/path-w-extension->mime-type "granules.iso_smap")))
    (is (= "application/iso19115+xml" (mt/path-w-extension->mime-type "granules.iso")))
    (is (= "application/iso19115+xml" (mt/path-w-extension->mime-type "granules.iso19115")))
    (is (= "application/dif+xml" (mt/path-w-extension->mime-type "granules.dif")))
    (is (= "text/csv" (mt/path-w-extension->mime-type "granules.csv")))
    (is (= "application/vnd.google-earth.kml+xml" (mt/path-w-extension->mime-type "granules.kml")))
    (is (= "application/opendata+json" (mt/path-w-extension->mime-type "granules.opendata"))))
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

(deftest validate-search-result-mime-type-test
  (testing "valid mime types"
    (mth/validate-request-mime-type "application/json" supported-mime-types)
    (mth/validate-request-mime-type "application/xml" supported-mime-types)
    (mth/validate-request-mime-type "*/*" supported-mime-types))
  (testing "invalid mime types"
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"The mime type \[application/foo\] is not supported."
          (mth/validate-request-mime-type "application/foo" supported-mime-types)))))
