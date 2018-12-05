(ns cmr.umm-spec.test.xml-to-umm-mappings.dif9.additional-attribute
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.dif9.additional-attribute :as aa]))

(defn- additional-attributes-test-file
  "Reads in the test file for these tests."
  []
  (slurp (io/resource "example-data/dif/C1214305813-AU_AADC.xml")))

(def expected-additional-attributes
  "This is the normal expected value for most of the tests."
   [{:Group "gov.nasa.gsfc.gcmd.additionalattribute",
     :Value "f4d30361-f1bd-4c44-9d23-99208fe35b7d",
     :Name "metadata.uuid",
     :Description "Not provided",
     :DataType "STRING"},
    {:Group "gov.nasa.gsfc.gcmd.additionalattribute",
     :Value "2015-11-29 18:23:23",
     :Name "metadata.extraction_date",
     :Description "Not provided",
     :DataType "STRING"},
    {:Group "gov.nasa.gsfc.gcmd.additionalattribute",
     :Value "8.1",
     :Name "metadata.keyword_version",
     :Description "Not provided",
     :DataType "FLOAT"},
    {:Group "gov.AdditionalAttributes",
     :Value "SOMEVALUE",
     :Name "Hello",
     :Description "Not provided",
     :DataType "STRING"}])


(defn- no-additional-attributes-test-file
  "Reads in the test file for these tests."
  []
  (slurp (io/resource "example-data/dif/C1214610485-SCIOPS.xml")))

(deftest test-dif9-additional-attributes

  (testing "Testing generating additional attributes from Extended_Metadata"
    (is (= expected-additional-attributes
           (aa/xml-elem->AdditionalAttributes (additional-attributes-test-file) true))))

  (testing "Testing generating additional attributes when they don't exist."
    (is (= nil
           (aa/xml-elem->AdditionalAttributes (no-additional-attributes-test-file) true)))))
