(ns cmr.common.test.mime-types
  "Tests for mime-type functions."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.mime-types-helper :as mth]))

(def unsupported-mime-types
  "Other mime-types."
  #{"text/html"
    "text/xml"
    "application/xhtml+xml"
    "applicaiton/dart"
    "application/javascript"
    "application/ecmascript"
    "application/pdf"})

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
    "application/opendata+json"
    })

(def optional-params
  "Some optional parameters to construct semicolon clauses."
  #{"charset=UTF-8"
    "charset=UTF-16"
    "header=present"
    ""})

(def all-mime-types
  (gen/elements (concat unsupported-mime-types supported-mime-types)))

(def mime-type-clause
  (gen/fmap (fn [[type params]]
              (if (empty? params)
                type
                (str type "; " params)))
            (gen/tuple all-mime-types (gen/elements optional-params))))

(def mime-type-strings
  (gen/fmap (fn [mime-type-vec] (str/join "," mime-type-vec))
            (gen/vector mime-type-clause)))

(defspec mime-type-from-headers 100
  (for-all [mime-type-str mime-type-strings]
    (let [headers {"accept" mime-type-str}
          mime-type (mt/mime-type-from-headers headers (disj supported-mime-types "*/*"))]
      (or ((disj supported-mime-types "*/*") mime-type)
          (= mime-type-str mime-type)
          (and (nil? mime-type)
               (or (empty? mime-type-str)
                   (= "*/*" mime-type-str)))))))

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
