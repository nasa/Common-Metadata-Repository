(ns cmr.transmit.test.kms
  "Contains unit tests for verifying KMS retrieval functionality."
  (:require [clojure.test :refer :all]
            [cmr.transmit.kms :as kms]))

(def sample-csv
  "Sample KMS csv file"
  (str
    "\"This is a sample for testing.\"\n"
    "field1,FIELD2,field3,Short_Name,UUID\n"
    "\"field1 value, (with commas)\",\"field2\",\"\",\"First Entry\",\"abc-123\"\n"
    "\"line with no short-name\",\"\",\"\",\"\",\"def-456\"\n"
    "\"field1 value 2\",\"field2 v2\",\"field3 value\",\"Last Entry\",\"xyz-789\"\n"))

(def sample-kms-entries
  "Sample KMS entries map"
  [{:uuid "abc-123",
    :short-name "First Entry",
    :field-2 "field2",
    :field-1 "field1 value, (with commas)"}
   {:uuid "def-456", :field-1 "line with no short-name"}
   {:uuid "xyz-789",
    :short-name "Last Entry",
    :field-3 "field3 value",
    :field-2 "field2 v2",
    :field-1 "field1 value 2"}])

(def sample-kms-entries-with-duplicates
  "Sample KMS entries map with duplicate short names"
  (conj sample-kms-entries {:uuid "123-abd", :short-name "First Entry", :field-1 "dupe-field-1"}))

(deftest validate-entries-test
  (testing "No duplicates"
    (is (nil? (seq (#'cmr.transmit.kms/find-invalid-entries sample-kms-entries)))))

  (testing "With duplicates"
    (let [expected [{:short-name "First Entry",
                     :field-1 "field1 value, (with commas)",
                     :field-2 "field2",
                     :uuid "abc-123"}
                    {:short-name "First Entry",
                     :field-1 "dupe-field-1",
                     :uuid "123-abd"}]
          actual (#'cmr.transmit.kms/find-invalid-entries sample-kms-entries-with-duplicates)]
      (is (= expected actual)))))

(deftest parse-entries-from-csv-test
  (let [expected {"First Entry" {:uuid "abc-123"
                                 :short-name "First Entry"
                                 :field-2 "field2"
                                 :field-1 "field1 value, (with commas)"}
                  "Last Entry" {:uuid "xyz-789"
                                :short-name "Last Entry"
                                :field-3 "field3 value"
                                :field-2 "field2 v2"
                                :field-1 "field1 value 2"}}
        actual (#'cmr.transmit.kms/parse-entries-from-csv "sample" sample-csv)]
    (is (= expected actual))))
