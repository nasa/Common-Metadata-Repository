(ns cmr.umm.test.echo10.collection
  "Tests parsing and generating ECHO10 Collection XML."
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [cmr.umm.test.generators :as umm-gen]

            ;; my code
            [cmr.umm.echo10.collection :as c]))

(defspec generate-collection-is-valid-xml-test 10
  (for-all [collection umm-gen/collections]
    (let [xml (c/generate-collection collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))
        ;;TODO verify fields once parsing is implemented
        ))))

(def valid-collection-xml
  "<Collection>
  <ShortName>MINIMAL</ShortName>
  <VersionId>1</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <LongName>A minimal valid collection</LongName>
  <DataSetId>A minimal valid collection V 1</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
</Collection>")

(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (c/validate-xml valid-collection-xml)))))
  (testing "invalid xml"
    (is (= ["Line 4 - cvc-datatype-valid.1.2.1: 'XXXX-12-31T19:00:00-05:00' is not a valid value for 'dateTime'."
            "Line 4 - cvc-type.3.1.3: The value 'XXXX-12-31T19:00:00-05:00' of element 'InsertTime' is not valid."
            "Line 5 - cvc-datatype-valid.1.2.1: 'XXXX-12-31T19:00:00-05:00' is not a valid value for 'dateTime'."
            "Line 5 - cvc-type.3.1.3: The value 'XXXX-12-31T19:00:00-05:00' of element 'LastUpdate' is not valid."]
           (c/validate-xml (s/replace valid-collection-xml "1999" "XXXX"))))))

(comment

(c/validate-xml (c/generate-collection (first (gen/sample-seq umm-gen/collections))))


)
