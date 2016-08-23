(ns cmr.umm-spec.test.umm-spec-test-util
  "Tests for cmr.umm-spec.util functions"
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.util :as util]))

(deftest test-parse-data-size
  (are [s expected] (= expected
                       (util/parse-data-sizes s))
    "2 k" [{:Size 2.0 :Unit "KB"}]
    "4,555.5mb" [{:Size 4555.5 :Unit "MB"}]
    "9.446 Tbytes" [{:Size 9.446 :Unit "TB"}]
    "pixel size: 1km, 500m, 250m" nil
    "4.3 GB (total volume)" [{:Size 4.3 :Unit "GB"}]
    "79 MB compressed; 214 MB uncompressed" [{:Size 79.0 :Unit "MB"} {:Size 214.0 :Unit "MB"}]
    "Total Volume: 40 Mbytes for XBT data; 10 Mbytes for TSG data" [{:Size 40.0 :Unit "MB"} {:Size 10.0 :Unit "MB"}]
    "1010 files; Total size: 2351.98 MB" [{:Size 2351.98 :Unit "MB"}]
    
    "Single layer, raster format, 13.2 megabytes; vector format, 1.2 megabytes"
    [{:Size 13.2 :Unit "MB"} {:Size 1.2 :Unit "MB"}]

    "173.596 Gbytes" [{:Size 173.596 :Unit "GB"}]

    ".5 GB" [{:Size 0.5 :Unit "GB"}]))

(deftest data-size-str-test
  (is (= (util/data-size-str [{:Size 92.4 :Unit "MB"} {:Size 0.4 :Unit "GB"}])
         "92.4 MB, 0.4 GB"))
  (is (= [{:Size 92.4 :Unit "MB"} {:Size 0.4 :Unit "GB"}]
         (util/parse-data-sizes (util/data-size-str [{:Size 92.4 :Unit "MB"} {:Size 0.4 :Unit "GB"}])))))
