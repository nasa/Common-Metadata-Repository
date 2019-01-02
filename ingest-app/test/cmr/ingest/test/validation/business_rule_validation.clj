(ns cmr.ingest.test.validation.business-rule-validation
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.validation.business-rule-validation :as bv]))

(def granule-test-concept {:concept-type :granule})
(def variable-test-concept {:concept-type :variable})

(deftest business-rules-for-granules
  (is (= [bv/delete-time-validation]
         (bv/business-rule-validations
          (:concept-type granule-test-concept)))))

(deftest business-rules-for-variables
  (is (= []
         (bv/business-rule-validations
          (:concept-type variable-test-concept)))))
