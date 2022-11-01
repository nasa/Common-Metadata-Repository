(ns cmr.ingest.test.api.generic-documents
  "This tests functions in generic documents."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as u :refer [are3]]
   [cmr.ingest.api.generic-documents :as gendoc]))

(deftest sub-concept-type-prefix
  "Tests the sub-concept-type-prefix function returns the correct value."

  (testing "Check all concept type prefixes"
    (are3 [expected spec-key version]
            (let [actual (gendoc/get-sub-concept-type-concept-id-prefix spec-key version)]
              (is (= expected actual) "sub concept type prefix does not match"))

          "dataqualitysummary->DQS" "DQS" :dataqualitysummary "1.0.0"
          "Grid->GRD" "GRD" :grid "0.0.1"
          "orderoptions->DQS" "OO" :orderoption "1.0.0"
          "serviceentry->DQS" "SE" :serviceentry "1.0.0"
          "serviceoption->DQS" "SO" :serviceoption "1.0.0"
          "fake->X" "X" :fake "a.b.c"
          "Empty test" "X" "" "")))
