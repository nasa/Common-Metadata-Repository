(ns cmr.transmit.test.kms
  "Contains unit tests for verifying KMS retrieval functionality."
  (:require [clojure.test :refer :all]
            [cmr.transmit.kms :as kms]))

(def sample-csv
  "Sample KMS csv file"
  (str
    "\"This is a sample for testing.\"\n"
    "Category,Series_Entity,Short_Name,Long_Name,UUID\n"
    "\"field1 value, (with commas)\",\"field2\",\"First Entry\",\"\",\"abc-123\"\n"
    "\"line with no short-name\",\"\",\"\",\"\",\"def-456\"\n"
    "\"field1 value 2\",\"field2 v2\",\"Last Entry\",\"This is the Last Entry\",\"xyz-789\"\n"))

(def sample-csv-mimetype
  "Sample KMS csv file for mimetype"
  (str
    "\"This is a sample for testing.\"\n"
    "MimeType,UUID\n"
    "\"application/gml+xml\",\"40bdf6e5-780c-43e2-ab8e-e5dfae4bd779\"\n"
    "\"application/gzip\",\"a8ee535a-8bc8-46fd-8b97-917bd7ea7666\"\n"))

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
    (is (nil? (seq (#'cmr.transmit.kms/find-invalid-entries sample-kms-entries :short-name)))))

  (testing "With duplicates"
    (let [expected [{:short-name "First Entry",
                     :field-1 "field1 value, (with commas)",
                     :field-2 "field2",
                     :uuid "abc-123"}
                    {:short-name "First Entry",
                     :field-1 "dupe-field-1",
                     :uuid "123-abd"}]
          actual (#'cmr.transmit.kms/find-invalid-entries sample-kms-entries-with-duplicates
                                                          :short-name)]
      (is (= expected actual)))))

(deftest parse-entries-from-csv-test
  (testing "Successful parsing for platforms"
    (let [expected [{:short-name "First Entry"
                     :series-entity "field2"
                     :category "field1 value, (with commas)"
                     :uuid "abc-123"}
                    {:short-name "Last Entry"
                     :long-name "This is the Last Entry"
                     :series-entity "field2 v2"
                     :category "field1 value 2"
                     :uuid "xyz-789"}]
          actual (#'cmr.transmit.kms/parse-entries-from-csv :platforms sample-csv)]
      (is (= expected actual))))

  (testing "Successful parsing for mimetype"
    (let [expected [{:mime-type "application/gml+xml",
                     :uuid "40bdf6e5-780c-43e2-ab8e-e5dfae4bd779"}
                    {:mime-type "application/gzip",
                     :uuid "a8ee535a-8bc8-46fd-8b97-917bd7ea7666"}]
          actual (#'cmr.transmit.kms/parse-entries-from-csv :mime-type sample-csv-mimetype)]
      (is (= expected actual))))

  (testing "Invalid subfield names in the CSV throws an exception"
    (is (thrown-with-msg?
          Exception
          #"Expected subfield names for instruments to be"
          (#'cmr.transmit.kms/parse-entries-from-csv :instruments sample-csv)))))

(deftest get-keywords-for-keyword-scheme-test
  (testing "Invalid keyword scheme requested throws an exception"
    (is (thrown? java.lang.AssertionError
          (kms/get-keywords-for-keyword-scheme nil :not-a-kms-scheme)))))
