(ns cmr.ingest.test.validation.umm-version-comparison
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.validation.validation :as validation]))

(deftest compare-versions-with-padded-zeros
  (is (> 0 (#'validation/compare-versions-with-padded-zeros "1.8" "1.9")))
  (is (< 0 (#'validation/compare-versions-with-padded-zeros "1.10" "1.9")))
  (is (< 0 (#'validation/compare-versions-with-padded-zeros "2.1" "1.10")))
  (is (< 0 (#'validation/compare-versions-with-padded-zeros "2.1" "1.9")))
  (is (< 0 (#'validation/compare-versions-with-padded-zeros "1.10.1" "1.9.2"))))

    
