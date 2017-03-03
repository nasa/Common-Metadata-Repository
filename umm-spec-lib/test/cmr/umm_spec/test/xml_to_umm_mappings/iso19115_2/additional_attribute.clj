(ns cmr.umm-spec.test.xml-to-umm-mappings.iso19115-2.additional-attribute
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.umm-spec.util :as spec-util]
   [cmr.common.util :as util]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute :as aa]))

(def expected-parsed-additional-attributes
  [{:Name "SIPSMetGenVersion"
    :Description "The version of the SIPSMetGen software used to produce the metadata file for this                                                granule                                            "
    :DataType "STRING"}
   {:Name "ThemeID"
    :Description "The identifier of the theme under which data are logically grouped"
    :DataType "STRING"}
   {:Name "AircraftID"
    :Description "The identifier of the airplane used by the FAA to uniquely identify each aircraft"
    :DataType "STRING"}])

(deftest dif10-metadata-additional-attributes-test
  (testing (str "Parse additional attributes from dataQualityInfo where there are multiple "
                "eos:EOS_AdditionalAttributeDescription under eos:reference.")
    ;; Note this should be invalid according to xml schema, but xml validation is not catching
    ;; the error. Here we just parse out all the descriptions as additional attributes
    (is (= expected-parsed-additional-attributes
           (map util/remove-nil-keys
                (#'aa/parse-data-quality-info-additional-attributes
                  (slurp (io/resource "example_data/iso19115/C1218-NSIDC-2.xml"))
                  (:sanitize? spec-util/default-parsing-options)))))))
