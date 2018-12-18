(ns cmr.sizing.tests.unit.formats
  (:require
   [clojure.test :refer :all]
   [cmr.sizing.formats :as formats]))

(def variable1 
  {:umm {:Dimensions [{:Size 100} {:Size 100}]
         :FillValues [{:Value 1} {:Value 100}]
         :ValidRanges [{:Min 1 :Max 100} {:Min 100 :Max 10000}]}})

(def variable2
  {:umm {:Dimensions [{:Size 200} {:Size 200}]
         :FillValues [{:Value 10} {:Value 1000}]
         :ValidRanges [{:Min 10 :Max 1000} {:Min 1000 :Max 100000}]}})

(deftest get-dimensionality 
  (is (= 10000 (#'formats/get-dimensionality variable1))))

(deftest get-avg-fill-value-digit-number
  (is (= 2 (#'formats/get-avg-fill-value-digit-number variable1))))

(deftest get-avg-valid-range-digit-number
  (is (= 3 (#'formats/get-avg-valid-range-digit-number variable1))))

(deftest estimate-ascii-size
  ;; Granule with variable1 has the estimated size of
  ;; 0.5 * 2 * 10000 + 0.5 * 3 * 10000 + 2 * (10000 - 1) = 44998
  ;; Granule with variable2 has the estimated size of
  ;; 0.5 * 3 * 40000 + 0.5 * 4 * 40000 + 2 * (40000 - 1) = 219998
  ;; Granule with both variable1 and variable2 has the estimated size of
  ;; 264996 bytes. 
  (is (= 529992.0 (#'formats/estimate-ascii-size 2 [variable1 variable2] {:request-id 1234}))))
  
