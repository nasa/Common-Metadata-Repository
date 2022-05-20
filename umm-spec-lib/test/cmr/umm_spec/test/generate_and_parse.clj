(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require
   [clj-time.core :as t]
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext :refer [checking checking-with-seed]]
   [cmr.common.util :as util :refer [update-in-each update-in-all are3]]
   [cmr.common.xml.simple-xpath :refer [select context]]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iu]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.migration.version.collection :as version-collection]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
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

(defn- convert-to-sets
  "Convert lists in the umm record to sets so order doesn't matter during comparison"
  [record]
  (-> record
      (update-in-each [:Platforms] update-in-each [:Instruments] update :ComposedOf set)
      (update-in-each [:Platforms] update :Instruments set)
      (update :Platforms set)
      (update :RelatedUrls set)
      (update-in-each [:DataCenters] update :ContactPersons set)
      (update :DataCenters set)
      (update :ContactPersons set)
      (update-in [:SpatialExtent] update :VerticalSpatialDomains set)))

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
  (seq (.listFiles (io/file (io/resource (str "example-data/" (name metadata-format)))))))

(defn- remove-vertical-spatial-domains
  "Remove the VerticalSpatialDomains from the SpatialExtent of the record."
  [record]
  (update-in record [:SpatialExtent] dissoc :VerticalSpatialDomains))

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
                    ;; Change fields to sets for comparison
                    ;; Also, dif9 changes VerticalSpatialDomain values when they contain Max and Min
                    ;;  Remove them from the comparison.
                    expected (convert-to-sets (if (= :dif target-format)
                                                (remove-vertical-spatial-domains expected)
                                                ;; Footprints don't exist in dif10 and echo10
                                                ;; it needs to be removed for round-trip comparison.
                                                (if (or (= :dif10 target-format)
                                                        (= :echo10 target-format)
                                                        (not (get-in expected
                                                              [:SpatialExtent :OrbitParameters :Footprints])))
                                                  (update-in expected [:SpatialExtent :OrbitParameters]
                                                             dissoc :Footprints)
                                                    expected)))
                    actual (convert-to-sets (if (= :dif target-format)
                                              (remove-vertical-spatial-domains actual)
                                              ;; remove Footprints if it's nil
                                              (if (get-in actual [:SpatialExtent :OrbitParameters :Footprints])
                                                actual
                                                (update-in actual [:SpatialExtent :OrbitParameters]
                                                           dissoc :Footprints))))
                    expected (util/remove-nil-keys expected)
                    actual (util/remove-nil-keys actual)]]

        ;; Taking the parsed UMM and converting it to another format produces the expected UMM
        (check-failure
         (is (= expected actual)
             (format "Parsing example file %s and converting to %s and then parsing again did not result in expected umm."
                     example-file target-format)))))))

(deftest roundtrip-example-collection-record
  (doseq [metadata-format tested-collection-formats]
    (testing (str metadata-format)
      (let [expected (expected-conversion/convert expected-conversion/example-collection-record metadata-format)
            actual (xml-round-trip :collection metadata-format expected-conversion/example-collection-record)
            expected (util/remove-nil-keys expected)
            actual (util/remove-nil-keys actual)]
        (is (= (convert-to-sets expected) (convert-to-sets actual)))))
    (testing (str metadata-format " UMM-C contains Footprints instead of SwathWidth.")
      ;; example-collection-record-no-swath contains Footprints instead of SwathWidth and SwatWidthUnit
      (let [expected (expected-conversion/convert expected-conversion/example-collection-record-no-swath metadata-format)
            expected (if (or (= :dif10 metadata-format) (= :echo10 metadata-format))
                       (as-> expected exp
                             (update-in exp [:SpatialExtent :OrbitParameters]
                                        assoc :SwathWidth (version-collection/get-swath-width exp) :SwathWidthUnit "Kilometer")
                             (update-in exp [:SpatialExtent :OrbitParameters] dissoc :Footprints))
                       expected)
            actual (xml-round-trip :collection metadata-format expected-conversion/example-collection-record-no-swath)
            actual (if (or (= :dif10 metadata-format) (= :echo10 metadata-format))
                     (update-in actual [:SpatialExtent :OrbitParameters] dissoc :Footprints)
                     actual)
            expected (util/remove-nil-keys expected)
            actual (util/remove-nil-keys actual)]
        (is (= (convert-to-sets expected) (convert-to-sets actual)))))))

(deftest validate-umm-json-example-record
  ;; Test that going from any format to UMM generates valid UMM.
  (doseq [[format filename] collection-format-examples
          :let [umm-c-record (xml-round-trip :collection format expected-conversion/example-collection-record)]]
    (testing (str format " to :umm-json")
      (is (empty? (generate-and-validate-xml :collection :umm-json umm-c-record))))))

;; This test starts with a umm record where the values of the record
;; are generated with different values every time. This test takes the
;; UMM record and converts it into another supported format, then converts it back
;; to UMM and then back to the other format then compares the
;; expected output with the result of the actual conversions. This test runs a record
;; through all of the supported formats.
(deftest roundtrip-generated-collection-records
  (checking "collection round tripping" 100
    [umm-record (gen/no-shrink umm-gen/umm-c-generator)
     metadata-format (gen/elements tested-collection-formats)]
    (let [;; CMR-8128 remove OrbitParameters 
          ;; there are many situations when a parameter is not
          ;; preserved after the roundtrip. We will have to make many special cases
          ;; in order to do the comparison. Since they have been tested in other tests, we will
          ;; just remove them from the generated roundtrip.
          ;; The following lists a few issues with roundtrip on OrbitParameters for dif10 and echo10:
          ;; 1. Footprints in umm doesn't exist and doesn't get translated so it can't be preserved
          ;; 2. StartCircularLatitudeUnit in umm can't be preserved when StartCircularLatitude doesn't exist.
          ;;    Assumed unit is used for translation only when StartCircularLatitude exists. This applies to iso1195 too.
          ;; 3. SwathWidthUnit doesn't exist in dif10 and echo10. Assumed unit is Kilometer
          ;;    so we have to convert the value and unit in umm-record to kilometer before round-trip comparison.
          ;; 4. SwathWidth can be 1.0E-1 in umm, translating to other formats it could be changed to 0.1
          umm-record (update-in umm-record [:SpatialExtent] dissoc :OrbitParameters)
          umm-record (js/parse-umm-c
                        (assoc umm-record
                               :DataDates [{:Date (t/date-time 2012)
                                            :Type "CREATE"}
                                           {:Date (t/date-time 2013)
                                            :Type "UPDATE"}]))
          expected (expected-conversion/convert umm-record metadata-format)
          actual (xml-round-trip :collection metadata-format umm-record)
          expected (util/remove-nil-keys expected)
          actual (util/remove-nil-keys actual)
          ;; Change fields to sets for comparison
          expected (convert-to-sets expected)
          actual (convert-to-sets actual)]
      (is (= expected actual)
          (str "Unable to roundtrip with format " metadata-format)))))

;; This test starts with a umm record where the values of the record
;; are generated with a seed number. When using a seed number you can
;; regenerate the same random values everytime.  This allows for repeated
;; testing and to re-create a test that failed with a specific seed number.
;; While this test specifically tests CMR-4047, it also provides other an example
;; of how to use the seed numbers from the test failure reports. This test takes the
;; UMM record and converts it into another supported format, then converts it back
;; to UMM and then back to the other format then compares the
;; expected output with the result of the actual conversions. This test runs a record
;; through all of the supported formats.
(deftest roundtrip-generated-collection-records-with-seed
  (checking-with-seed "collection round tripping seed" 100 1496683985472
    [umm-record (gen/no-shrink umm-gen/umm-c-generator)
     metadata-format (gen/elements tested-collection-formats)]
    (let [;; CMR-8128 remove OrbitParameters for the same reason as the previous test.
          umm-record (update-in umm-record [:SpatialExtent] dissoc :OrbitParameters)
          umm-record (js/parse-umm-c
                      (assoc umm-record
                             :DataDates [{:Date (t/date-time 2012)
                                          :Type "CREATE"}
                                         {:Date (t/date-time 2013)
                                          :Type "UPDATE"}]))
          expected (expected-conversion/convert umm-record metadata-format)
          actual (xml-round-trip :collection metadata-format umm-record)
          expected (util/remove-nil-keys expected)
          actual (util/remove-nil-keys actual)
          ;; Change fields to sets for comparison
          expected (convert-to-sets expected)
          actual (convert-to-sets actual)]
      (is (= expected actual)
          (str "Unable to roundtrip with format " metadata-format)))))

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
