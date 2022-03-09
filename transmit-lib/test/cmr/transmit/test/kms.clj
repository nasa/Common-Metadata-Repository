(ns cmr.transmit.test.kms
  "Contains unit tests for verifying KMS retrieval functionality."
  (:require [clojure.test :refer :all]
            [cmr.common.util :refer [are3]]
            [cmr.transmit.kms :as kms]))

(def sample-csv
  "Sample KMS csv file"
  (str
    "\"This is a sample for testing.\"\n"
    "Basis,Category,Sub_Category,Short_Name,Long_Name,UUID\n"
    "\"B1\",\"field1 value, (with commas)\",\"field2\",\"First Entry\",\"\",\"abc-123\"\n"
    "\"B1\",\"line with no short-name\",\"\",\"\",\"\",\"def-456\"\n"
    "\"B2\",\"field1 value 2\",\"field2 v2\",\"Last Entry\",\"This is the Last Entry\",\"xyz-789\"\n"))

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
    (let [expected [{:basis "B1"
                     :short-name "First Entry"
                     :sub-category "field2"
                     :category "field1 value, (with commas)"
                     :uuid "abc-123"}
                    {:basis "B2"
                     :short-name "Last Entry"
                     :long-name "This is the Last Entry"
                     :sub-category "field2 v2"
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

  (testing "Invalid subfield names in the CSV returns nothing"
    (is (nil? (#'cmr.transmit.kms/parse-entries-from-csv :instruments sample-csv)))))

(deftest get-keywords-for-keyword-scheme-test
  (testing "Invalid keyword scheme requested throws an exception"
    (is (thrown? java.lang.AssertionError
          (kms/get-keywords-for-keyword-scheme nil :not-a-kms-scheme)))))

(deftest subfield-names-valid-test
  (let [good-headers [:basis :category :sub-category :short-name :long-name :uuid]]
    (testing "good matches"
      (is (true? (#'cmr.transmit.kms/subfield-names-valid? :platforms good-headers))))
  (testing "bad matches"
    (is (false? (#'cmr.transmit.kms/subfield-names-valid? :platforms [])) "empty list")
    (is (false? (#'cmr.transmit.kms/subfield-names-valid? :fake good-headers)) "known keyword"))))

(deftest scheme-overrides-test-3
  (testing "Test scheme override JSON)"
    (are3
     [expected provided]
     (do
       (cmr.transmit.config/set-kms-scheme-override-json! provided)
       (let [result (#'cmr.transmit.kms/scheme-overrides)]
         (is (= expected result))))

     "expected a static and a mimetype result"
     {:platforms "static" :mime-type "mimetype?format=csv"}
     "{\"platforms\": \"static\", \"mime-type\": \"mimetype?format=csv\"}"

     "expected a static result"
     {:platforms "static"} "{\"platforms\": \"static\"}"

     "expected an empty map"
     {} "{}"

     "bad JSON still returns something and not an exception"
     nil "{bad JSON here}"

     ;; Doing this test last will restore the default setting for the
     ;; kms-schema-override-json property - cleanup
     "expected a nil"
     nil "")))

(deftest keyword-scheme->kms-resource-test
  (testing "Schema overrides change the gcmd resource"
    (cmr.transmit.config/set-kms-scheme-override-json!
     "{\"platforms\": \"static\", \"extra\": \"extra?format=csv\"}")
    (let [result-platform (#'cmr.transmit.kms/keyword-scheme->kms-resource :platforms)
          result-concepts (#'cmr.transmit.kms/keyword-scheme->kms-resource :concepts)
          result-extra (#'cmr.transmit.kms/keyword-scheme->kms-resource :extra)]
      (is (= "static" result-platform) "platform changed")
      (is (= "idnnode?format=csv" result-concepts) "idn node stays the same")
      (is (= "extra?format=csv" result-extra) "extra property injected"))
    ;; reset for future tests
    (cmr.transmit.config/set-kms-scheme-override-json! "")))

(def spatial-keywords-3-subregions
  (str "\"Keyword Version: 8.6\",\"Revision: 2018-10-24 08:20:28\",\"Timestamp: 2018-11-05 09:03:32\",\"Terms Of Use: http://gcmd.nasa.gov/r/l/TermsOfUse\",\"The most up to date XML representations can be found here: https://gcmdservices.gsfc.nasa.gov/kms/concepts/concept_scheme/locations/?format=xml\"\"Case native\"\n"
       "Location_Category,Location_Type,Location_Subregion1,Location_Subregion2,Location_Subregion3,UUID\n"
       "\"CONTINENT\",\"\",\"\",\"\",\"\",\"0a672f19-dad5-4114-819a-2eb55bdbb56a\"\n"
       "\"CONTINENT\",\"AFRICA\",\"\",\"\",\"\",\"2ca1b865-5555-4375-aa81-72811335b695\"\n"
       "\"OCEAN\",\"ATLANTIC OCEAN\",\"NORTH ATLANTIC OCEAN\",\"MEDITERRANEAN SEA\",\"ADRIATIC SEA\",\"7b93c892-2fc4-417b-a4da-5c8a2fca361b\"\n"))

(def spatial-keywords-4-subregions
  (str "\"Keyword Version: 8.6\",\"Revision: 2018-10-24 08:20:28\",\"Timestamp: 2018-11-05 09:03:32\",\"Terms Of Use: http://gcmd.nasa.gov/r/l/TermsOfUse\",\"The most up to date XML representations can be found here: https://gcmdservices.gsfc.nasa.gov/kms/concepts/concept_scheme/locations/?format=xml\"\"Case native\"\n"
       "Location_Category,Location_Type,Location_Subregion1,Location_Subregion2,Location_Subregion3,Location_Subregion4,UUID\n"
       "\"CONTINENT\",\"\",\"\",\"\",\"\",\"\",\"0a672f19-dad5-4114-819a-2eb55bdbb56a\"\n"
       "\"CONTINENT\",\"AFRICA\",\"\",\"\",\"\",\"\",\"2ca1b865-5555-4375-aa81-72811335b695\"\n"
       "\"OCEAN\",\"ATLANTIC OCEAN\",\"NORTH ATLANTIC OCEAN\",\"MEDITERRANEAN SEA\",\"ADRIATIC SEA\",\"GULF OF TRIESTE\",\"7b93c892-2fc4-417b-a4da-5c8a2fca361b\"\n"))

(deftest keyword-scheme->kms-resource-test
  (testing "Using the 3 subregion and the 4 subregion KMS values to read in spatial-keywords
           (location). The end parsing should be the same until the CMR supports only subregion-4.)."
    (is (= (#'kms/parse-entries-from-csv :spatial-keywords spatial-keywords-3-subregions)
           (#'kms/parse-entries-from-csv :spatial-keywords spatial-keywords-4-subregions)))))
