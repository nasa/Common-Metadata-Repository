(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.common.util :refer [update-in-each]]
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.core :as core]
            [cmr.umm-spec.simple-xpath :refer [select context]]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso-xml-to-umm]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso-umm-to-xml]
            [cmr.common.util :refer [are2]]
            [cmr.umm-spec.test.umm-generators :as umm-gen]))

(defn xml-round-trip
  "Returns record after being converted to XML and back to UMM through
  the given to-xml and to-umm mappings."
  [record format]
  (let [metadata-xml (core/generate-metadata :collection format record)]
    ;; validate xml
    ;; Since our UMM JSON schema is not complete in defining things like enumeration values for
    ;; controlled vocabulary fields, the generator generated fields would not create validate field
    ;; values for those fields (e.g. Dataset_Language field). Until we change the schema to be
    ;; explicit on those enumeration values and other things, we can't turn validation on xml on.
    ;; See CMR-1990
    ; (is (empty? (core/validate-xml :collection format metadata-xml)))
    (core/parse-metadata :collection format metadata-xml)))

(deftest roundtrip-gen-parse
  (are2 [metadata-format]
        (= (expected-conversion/convert expected-conversion/example-record metadata-format)
           (xml-round-trip expected-conversion/example-record metadata-format))

        "echo10"
        :echo10

        "dif9"
        :dif

        "dif10"
        :dif10

        "iso-smap"
        :iso-smap

        "ISO19115-2"
        :iso19115))

(deftest generate-valid-xml
  (testing "valid XML is generated for each format"
    (are [fmt]
         (empty?
           (core/validate-xml :collection fmt
                              (core/generate-metadata :collection fmt expected-conversion/example-record)))
         :echo10
         :dif
         :dif10
         :iso-smap
         :iso19115)))

(defspec roundtrip-generator-gen-parse 1000
  (for-all [umm-record umm-gen/umm-c-generator
            metadata-format (gen/elements [:echo10 :dif :dif10 :iso-smap :iso19115])]
    (is (= (expected-conversion/convert umm-record metadata-format)
           (xml-round-trip umm-record metadata-format)))))

(defn- parse-iso19115-projects-keywords
  "Returns the parsed projects keywords for the given ISO19115-2 xml"
  [metadata-xml]
  (let [md-data-id-el (first (select (context metadata-xml) iso-xml-to-umm/md-data-id-base-xpath))]
    (seq (#'iso-xml-to-umm/descriptive-keywords md-data-id-el "project"))))

;; Info in UMM Projects field is duplicated in ISO191152 xml in two different places.
;; We parse UMM Projects from the gmi:MI_Operation, not from gmd:descriptiveKeywords.
;; This test is to verify that we populate UMM Projects in gmd:descriptiveKeywords correctly as well.
(defspec iso19115-projects-keywords 100
  (for-all [umm-record umm-gen/umm-c-generator]
    (let [metadata-xml (core/generate-metadata :collection :iso19115 umm-record)
          projects (:Projects (core/parse-metadata :collection :iso19115 metadata-xml))
          expected-projects-keywords (seq (map #'iso-umm-to-xml/generate-title projects))]
      (is (= expected-projects-keywords
             (parse-iso19115-projects-keywords metadata-xml))))))

(comment


  (is (= (expected-conversion/convert user/failing-value :echo10)
         (xml-round-trip user/failing-value :echo10)))


  )
