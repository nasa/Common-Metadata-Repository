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
            [cmr.umm-spec.test.umm-record-sanitizer :as sanitize]
            [cmr.umm-spec.core :as core]
            [cmr.common.xml.simple-xpath :refer [select context]]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso-xml-to-umm]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso-umm-to-xml]
            [cmr.umm-spec.iso-keywords :as kws]
            [cmr.umm-spec.iso19115-2-util :as iu]
            [cmr.umm-spec.umm-to-xml-mappings.echo10 :as echo10]
            [cmr.common.util :refer [are2]]
            [cmr.umm-spec.test.umm-generators :as umm-gen]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]))

(def tested-collection-formats
  "Seq of formats to use in round-trip conversion and XML validation tests."
  [:dif :dif10 :echo10 :iso19115 :iso-smap])

(def test-context (lkt/setup-context-for-test lkt/sample-keyword-map))

(def tested-service-formats
  "Seq of formats to use in round-trip conversion and XML validation tests."
  [:serf])

(def collection-destination-formats
  "Converting to these formats is tested in the roundrobin test."
  [:echo10 :dif10 :dif :iso19115 :iso-smap])

(def collection-format-examples
  "Map of format type to example file"
  {:dif "dif.xml"
   :dif10 "dif10.xml"
   :echo10 "echo10.xml"
   :iso19115 "iso19115.xml"
   :iso-smap "iso_smap.xml"})

(defn xml-round-trip
  "Returns record after being converted to XML and back to UMM through
  the given to-xml and to-umm mappings."
  [concept-type metadata-format record]
  (let [metadata-xml (core/generate-metadata test-context record metadata-format)]
    ;; validate against xml schema
    (is (empty? (core/validate-xml concept-type metadata-format metadata-xml)))
    (core/parse-metadata test-context concept-type metadata-format metadata-xml)))

(defn- generate-and-validate-xml
  "Returns a vector of errors (empty if none) from attempting to convert the given UMM record
  to valid XML in the given format."
  [concept-type metadata-format record]
  (let [metadata-xml (core/generate-metadata test-context record metadata-format)]
    (core/validate-xml concept-type metadata-format metadata-xml)))

(deftest roundtrip-example-collection-record
  (doseq [metadata-format tested-collection-formats]
    (testing (str metadata-format)
      (let [expected (expected-conversion/convert expected-conversion/example-collection-record metadata-format)
            actual (xml-round-trip :collection metadata-format expected-conversion/example-collection-record)]
        (is (= expected actual))))))

(deftest roundtrip-example-service-record
  (is (= (expected-conversion/convert expected-conversion/example-service-record :serf)
         (xml-round-trip :service :serf expected-conversion/example-service-record))))

(deftest roundrobin-collection-example-record
  (doseq [[origin-format filename] collection-format-examples
          :let [metadata (slurp (io/resource (str "example_data/" filename)))
                umm-c-record (core/parse-metadata test-context :collection origin-format metadata)]
          dest-format collection-destination-formats
          :when (not= origin-format dest-format)]
    (testing (str origin-format " to " dest-format)
      (is (empty? (generate-and-validate-xml :collection dest-format umm-c-record))))))

(defspec roundtrip-generated-collection-records 100
  (for-all [umm-record (gen/no-shrink umm-gen/umm-c-generator)
            metadata-format (gen/elements tested-collection-formats)]
    (is (= (expected-conversion/convert umm-record metadata-format)
           (xml-round-trip :collection metadata-format umm-record)))))

(defspec roundtrip-generated-service-records 100
  (for-all [umm-record (gen/no-shrink umm-gen/umm-s-generator)
            metadata-format (gen/elements tested-service-formats)]
    (is (= (expected-conversion/convert umm-record metadata-format)
           (xml-round-trip :service metadata-format umm-record)))))
(comment

  (is (= (expected-conversion/convert failing-value :serf)
         (xml-round-trip :service :serf failing-value)))

  (= (type (expected-conversion/convert user/failing-value :serf))
     (type (xml-round-trip :service :serf user/failing-value)))

  (is (= (:Platforms (expected-conversion/convert user/failing-value :serf))
         (:Platforms (xml-round-trip :service :serf user/failing-value))))

  (is (= (:Responsibilities (expected-conversion/convert expected-conversion/example-service-record :serf))
         (:Responsibilities (xml-round-trip :service :serf expected-conversion/example-service-record))))

  (is (= (expected-conversion/convert expected-conversion/example-service-record :serf)
         (xml-round-trip :service :serf expected-conversion/example-service-record)))

  (is (= (:Responsibilities (:Party (last (:Responsibilities (expected-conversion/convert expected-conversion/example-service-record :serf)))))
         (:Responsibilities (:Party (last (:Responsibilities (xml-round-trip :service :serf expected-conversion/example-service-record))))))))


(defn- parse-iso19115-projects-keywords
  "Returns the parsed projects keywords for the given ISO19115-2 xml"
  [metadata-xml]
  (let [md-data-id-el (first (select (context metadata-xml) iso-xml-to-umm/md-data-id-base-xpath))]
    (seq (#'kws/descriptive-keywords md-data-id-el "project"))))

;; Info in UMM Projects field is duplicated in ISO191152 xml in two different places.
;; We parse UMM Projects from the gmi:MI_Operation, not from gmd:descriptiveKeywords.
;; This test is to verify that we populate UMM Projects in gmd:descriptiveKeywords correctly as well.
(defspec iso19115-projects-keywords 100
  (for-all [umm-record umm-gen/umm-c-generator]
    (let [metadata-xml (core/generate-metadata test-context umm-record :iso19115)
          projects (:Projects (core/parse-metadata test-context :collection :iso19115 metadata-xml))
          expected-projects-keywords (seq (map iu/generate-title projects))]
      (is (= expected-projects-keywords
             (parse-iso19115-projects-keywords metadata-xml))))))

(def minimal-umm-c
  "UMM-C with the bare minimum number of fields. It does not include all required fields because
  there is existing data in the system which does not contain all of the required UMM-C fields. We
  are testing that even without all the required UMM-C fields, we still produce valid XML in each
  of the formats."
  (js/parse-umm-c {:ShortName "foo" :Version "bar"}))

(deftest minimal-dif10
  (is (empty? (generate-and-validate-xml :collection :dif10 minimal-umm-c))))

(comment

  (is (= (expected-conversion/convert user/failing-value :iso19115)
         (xml-round-trip :collection :iso19115 user/failing-value)))

  ;; random XML gen
  (def metadata-format :echo10)
  (def metadata-format :dif)
  (def metadata-format :dif10)
  (def metadata-format :iso19115)
  (def metadata-format :iso-smap)
  (def metadata-format :serf)

  ;; UMM concept-type
  (def concept-type :collection)
  (def concept-type :service)


  (def sample-record (first (gen/sample (gen/such-that
                                          #(not-any? :Instruments (:Platforms %))
                                          umm-gen/umm-c-generator) 1)))

  ;; Evaluate this expression to use user/failing-value in the following expressions.
  (def sample-record failing-value)
  (def umm-record failing-value)

  ;; Evaluate this expression to use the standard UMM example record.
  (def sample-record expected-conversion/example-service-record)

  ;; Evaluate to print generated metadata from the record selected above.
  (println (core/generate-metadata test-context sample-record [:collection :dif10]))

  ;; our simple example record
  (core/generate-metadata :collection metadata-format sample-record)
  (core/generate-metadata :service metadata-format expected-conversion/example-service-record)

  (core/validate-xml :service metadata-format metadata-xml)

  ;; round-trip
  (xml-round-trip concept-type metadata-format sample-record)

  ;; Evaluate to see diff between expected conversion and result of XML round trip.
  (is (= (expected-conversion/convert sample-record metadata-format)
         (xml-round-trip concept-type metadata-format sample-record)))

  ;; for generated test failures
  (is (= (expected-conversion/convert user/failing-value metadata-format)
         (xml-round-trip concept-type metadata-format user/failing-value))))
