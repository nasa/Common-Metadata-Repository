(ns cmr.umm-spec.test.umm-spec-test-util
  "Tests for cmr.umm-spec.util functions"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.util :as util]))


(deftest test-parse-data-sizes
  (are [s expected] (= expected
                       (util/parse-data-sizes s))
    "2 k" [{:Size 2 :Unit "KB"}]
    "4,555.5mb" [{:Size 4555.5 :Unit "MB"}]
    "9.446 Tbytes" [{:Size 9.446 :Unit "TB"}]
    "pixel size: 1km, 500m, 250m" nil
    "4.3 GB (total volume)" [{:Size 4.3 :Unit "GB"}]
    "79 MB compressed; 214 MB uncompressed" [{:Size 79 :Unit "MB"} {:Size 214 :Unit "MB"}]
    "Total Volume: 40 Mbytes for XBT data; 10 Mbytes for TSG data" [{:Size 40 :Unit "MB"} {:Size 10 :Unit "MB"}]
    "1010 files; Total size: 2351.98 MB" [{:Size 2351.98 :Unit "MB"}]

    "Single layer, raster format, 13.2 megabytes; vector format, 1.2 megabytes"
    [{:Size 13.2 :Unit "MB"} {:Size 1.2 :Unit "MB"}]

    "173.596 Gbytes" [{:Size 173.596 :Unit "GB"}]

    ".5 GB" [{:Size '.5 :Unit "GB"}])

  (are3 [s expected]
    (is (= expected (util/parse-data-sizes s)))

    "Basic bytes to KB"
    "1234 bytes" [{:Size 1.234 :Unit "KB"}]

    "Bytes to KB with commas"
    "2,894,890 bytes" [{:Size 2894.89 :Unit "KB"}]

    "KB with sanitize"
    "2 k" [{:Size 2 :Unit "KB"}]))

(deftest data-size-str-test
  (is (= (util/data-size-str [{:Size 92.4 :Unit "MB"} {:Size 0.4 :Unit "GB"}])
         "92.4 MB, 0.4 GB"))
  (is (= [{:Size 92.4 :Unit "MB"} {:Size 0.4 :Unit "GB"}]
         (util/parse-data-sizes (util/data-size-str [{:Size 92.4 :Unit "MB"} {:Size 0.4 :Unit "GB"}])))))

(deftest format-isbn
  (are3 [original-isbn formatted-isbn]
    (is (= formatted-isbn (util/format-isbn original-isbn)))

    "Remove dashes"
    "123-456-789-1" "1234567891"

    "No changes"
    "1234567891" "1234567891"

    "Remove ISBN"
    "ISBN-12345678" "12345678"

    "Remove ISSN"
    "ISSN-12345678" "12345678"

    "No numbers"
    "ISBN" nil))

(deftest truncation
  (testing "truncation with sanitize? option"
    (are3 [str size sanitize? expected]
      (is (= expected (util/truncate str size sanitize?)))

      "Sanitize? true, truncate"
      "ABCDEFG" 4 true "ABCD"

      "Sanitize? false, do not truncate"
      "ABCDEFG" 4 false "ABCDEFG"

      "Sanitize? true, length equal to max"
      "ABCDE" 5 true "ABCDE"))
  (testing "truncation with default and sanitize? option"
    (are3 [str size sanitize? expected]
      (is (= expected (util/truncate-with-default str size sanitize?)))

      "Sanitize? true, nil value"
      nil 100 true "Not provided"

      "Sanitize? false, nil value"
      nil 100 false nil

      "Sanitize? true, truncate"
      "ABCDEFG" 4 true "ABCD"

      "Sanitize? false, do not truncate"
      "ABCDEFG" 4 false "ABCDEFG")))
