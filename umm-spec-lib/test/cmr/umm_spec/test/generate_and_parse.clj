(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require
   [clj-time.core :as t]
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext :refer [checking]]
   [cmr.common.util :refer [update-in-each are2]]
   [cmr.common.xml.simple-xpath :refer [select context]]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iu]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.test.umm-record-sanitizer :as sanitize]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.umm-to-xml-mappings.echo10 :as echo10]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso-umm-to-xml]
   [cmr.umm-spec.validation.umm-spec-validation-core :as umm-validation]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso-xml-to-umm]
   [com.gfredericks.test.chuck.clojure-test :as ct :refer [for-all]]))

(def tested-collection-formats
  "Seq of formats to use in round-trip conversion and XML validation tests."
  [:dif :dif10 :echo10 :iso19115 :iso-smap])

(def test-context (lkt/setup-context-for-test))

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
  ([concept-type metadata-format record]
   (xml-round-trip concept-type metadata-format record false))
  ([concept-type metadata-format record print-xml?]
   (let [metadata-xml (core/generate-metadata test-context record metadata-format)]
     (when print-xml? (println metadata-xml))
     ;; validate against xml schema
     (is (empty? (core/validate-xml concept-type metadata-format metadata-xml)))
     (core/parse-metadata test-context concept-type metadata-format metadata-xml))))

(defn- generate-and-validate-xml
  "Returns a vector of errors (empty if none) from attempting to convert the given UMM record
  to valid XML in the given format."
  [concept-type metadata-format record]
  (let [metadata-xml (core/generate-metadata test-context record metadata-format)]
    (core/validate-metadata concept-type metadata-format metadata-xml)))

(defn example-files
  "Returns a set of example metadata files in the given format."
  [metadata-format]
  (seq (.listFiles (io/file (io/resource (str "example_data/" (name metadata-format)))))))

(deftest roundtrip-example-metadata
  (let [failed-atom (atom false)
        check-failure (fn [result]
                        (when-not result (reset! failed-atom true)))]
    (doseq [metadata-format tested-collection-formats
            example-file (example-files metadata-format)
            :when (not @failed-atom)
            :let [metadata (slurp example-file)
                  umm (js/parse-umm-c
                        (assoc
                          (core/parse-metadata test-context :collection metadata-format metadata)
                          :DataDates [{:Date (t/date-time 2012)
                                       :Type "CREATE"}
                                      {:Date (t/date-time 2013)
                                       :Type "UPDATE"}]))]]
      ;; input file is valid
      (check-failure
       (is (empty? (core/validate-xml :collection metadata-format metadata))
           (format "Source file %s is not valid %s XML" example-file metadata-format)))

      ;; Parsed UMM is valid against the JSON schema
      (check-failure
       (is (empty? (js/validate-umm-json (umm-json/umm->json umm) :collection))
           (format "Parsing source file %s in format %s to UMM produced invalid UMM JSON."
                   example-file metadata-format)))

      ;; Parsed UMM is valid against the UMM validation rules
      (check-failure
       (is (empty? (umm-validation/validate-collection umm))
           (format "Parsing source file %s in format %s to UMM had validation errors"
                   example-file metadata-format)))

      (doseq [target-format tested-collection-formats
              :when (not @failed-atom)
              :let [expected (expected-conversion/convert umm target-format)
                    expected (update-in-each expected [:Platforms] update-in-each [:Instruments] 
                               #(assoc % :NumberOfInstruments (let [ct (count (:ComposedOf %))]
                                                                (when (> ct 0) ct))))
                    actual (xml-round-trip :collection target-format umm)
                    actual (if (= :iso-smap target-format)
                             actual
                             (update-in-each actual [:Platforms] update-in-each [:Instruments] 
                               #(assoc % :NumberOfInstruments (let [ct (count (:ComposedOf %))]
                                                                (when (> ct 0) ct))))) 
                    ;; The RelatedUrls field get reshuffled during the conversions,
                    ;; so we compare RelatedUrls as a set.
                    expected (update expected :RelatedUrls set)
                    actual (update actual :RelatedUrls set)]]

        ;; Taking the parsed UMM and converting it to another format produces the expected UMM
        (check-failure
         (is (= expected actual)

             (format "Parsing example file %s and converting to %s and then parsing again did not result in expected umm."
                     example-file target-format)))))))

(deftest roundtrip-example-collection-record
  (doseq [metadata-format tested-collection-formats]
    (testing (str metadata-format)
      (let [expected (expected-conversion/convert expected-conversion/example-collection-record metadata-format)
            actual (xml-round-trip :collection metadata-format expected-conversion/example-collection-record)]
        (is (= expected actual))))))

(deftest roundtrip-example-service-record
  (is (= (expected-conversion/convert expected-conversion/example-service-record :serf)
         (xml-round-trip :service :serf expected-conversion/example-service-record))))

(deftest validate-umm-json-example-record
  ;; Test that going from any format to UMM generates valid UMM.
  (doseq [[format filename] collection-format-examples
          :let [umm-c-record (xml-round-trip :collection format expected-conversion/example-collection-record)]]
    (testing (str format " to :umm-json")
      (is (empty? (generate-and-validate-xml :collection :umm-json umm-c-record))))))

(deftest roundtrip-generated-collection-records
  (checking "collection round tripping" 100
    [umm-record (gen/no-shrink umm-gen/umm-c-generator)
     metadata-format (gen/elements tested-collection-formats)]
    (let [umm-record (js/parse-umm-c
                        (assoc umm-record
                               :DataDates [{:Date (t/date-time 2012)
                                            :Type "CREATE"}
                                           {:Date (t/date-time 2013)
                                            :Type "UPDATE"}]))
          expected (expected-conversion/convert umm-record metadata-format)
          actual (xml-round-trip :collection metadata-format umm-record)
         
          ;; changing everything to set comparison
          expected (update-in-each expected [:Platforms] update-in-each [:Instruments] update :ComposedOf set)
          actual (update-in-each actual [:Platforms] update-in-each [:Instruments] update :ComposedOf set)
          expected (update-in-each expected [:Platforms] update :Instruments set)
          actual (update-in-each actual [:Platforms] update :Instruments set)
          expected (update expected :Platforms set)
          actual (update actual :Platforms set)
 
          ;; The RelatedUrls field get reshuffled during the conversions,
          ;; so we compare RelatedUrls as a set.
          expected (update expected :RelatedUrls set)
          actual (update actual :RelatedUrls set)]
      (is (= expected actual)
          (str "Unable to roundtrip with format " metadata-format)))))

(deftest roundtrip-generated-service-records
  (checking "service round tripping" 100
    [umm-record (gen/no-shrink umm-gen/umm-s-generator)
     metadata-format (gen/elements tested-service-formats)]
    (is (= (expected-conversion/convert umm-record metadata-format)
           (xml-round-trip :service metadata-format umm-record)))))

(defn- parse-iso19115-projects-keywords
  "Returns the parsed projects keywords for the given ISO19115-2 xml"
  [metadata-xml]
  (let [md-data-id-el (first (select (context metadata-xml) iso-xml-to-umm/md-data-id-base-xpath))]
    (seq (#'kws/descriptive-keywords md-data-id-el "project"))))

;; Info in UMM Projects field is duplicated in ISO191152 xml in two different places.
;; We parse UMM Projects from the gmi:MI_Operation, not from gmd:descriptiveKeywords.
;; This test is to verify that we populate UMM Projects in gmd:descriptiveKeywords correctly as well.
(deftest iso19115-projects-keywords
  (checking "Converting iso19115 project keywords" 100
    [umm-record umm-gen/umm-c-generator]
    (let [metadata-xml (core/generate-metadata test-context umm-record :iso19115)
          projects (:Projects (core/parse-metadata test-context :collection :iso19115 metadata-xml))
          expected-projects-keywords (seq (map iu/generate-title projects))]
      (is (= expected-projects-keywords
             (parse-iso19115-projects-keywords metadata-xml))))))
