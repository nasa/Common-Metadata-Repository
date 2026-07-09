(ns cmr.audit-simulator.metadata-test
  (:require
   [clojure.test :refer :all]
   [cmr.audit-simulator.metadata :as metadata]))

(deftest validation-report-finds-intentional-errors
  (let [report (metadata/validation-report)
        invalid-records (remove :valid? report)]
    (is (= 3 (count report)))
    (is (= 2 (count invalid-records)))))
