(ns cmr.umm.test.echo10.granule
  "Tests parsing and generating ECHO10 Granule XML."
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [cmr.umm.test.generators :as umm-gen]
            [cmr.umm.echo10.granule :as g]
            [cmr.umm.granule :as umm-g]))

(def valid-granule-xml-w-datasetid
  "<Granule>
  <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
  <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
  <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
  <Collection>
  <DataSetId>AQUARIUS_L1A_SSS:1</DataSetId>
  </Collection>
  <RestrictionFlag>0.0</RestrictionFlag>
  <Orderable>false</Orderable>
  </Granule>")

(def valid-granule-xml-w-sn-ver
  "<Granule>
  <GranuleUR>GranuleUR100</GranuleUR>
  <InsertTime>2010-01-05T05:30:30.550-05:00</InsertTime>
  <LastUpdate>2010-01-05T05:30:30.550-05:00</LastUpdate>
  <Collection>
  <ShortName>TESTCOLL-100</ShortName>
  <VersionId>1.0</VersionId>
  </Collection>
  <RestrictionFlag>0.0</RestrictionFlag>
  <Orderable>true</Orderable>
  </Granule>")

(defspec generate-granule-is-valid-xml-test 100
  (for-all [granule umm-gen/granules]
    (let [xml (g/generate-granule granule)]
      (and
        (> (count xml) 0)
        (= 0 (count (g/validate-xml xml)))))))

(defspec generate-and-parse-granule-test 100
  (for-all [granule umm-gen/granules]
    (let [xml (g/generate-granule granule)
          parsed (g/parse-granule xml)]
      (= parsed granule))))

(deftest validate-xml
  (testing "valid xml1"
    (is (= 0 (count (g/validate-xml valid-granule-xml-w-datasetid)))))
  (testing "valid xml2"
    (is (= 0 (count (g/validate-xml valid-granule-xml-w-sn-ver)))))
  (testing "invalid xml"
    (is (= ["Line 3 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Line 3 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'InsertTime' is not valid."
            "Line 4 - cvc-datatype-valid.1.2.1: 'XXXX-01-05T05:30:30.550-05:00' is not a valid value for 'dateTime'."
            "Line 4 - cvc-type.3.1.3: The value 'XXXX-01-05T05:30:30.550-05:00' of element 'LastUpdate' is not valid."]
           (g/validate-xml (s/replace valid-granule-xml-w-sn-ver "2010" "XXXX"))))))
