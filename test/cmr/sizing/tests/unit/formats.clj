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

(def variable-missing-Dimensions
  {:umm {:FillValues [{:Value 10} {:Value 1000}]
         :ValidRanges [{:Min 10 :Max 1000} {:Min 1000 :Max 100000}]}})

(def variable-missing-FillValues
  {:umm {:Dimensions [{:Size 200} {:Size 200}]
         :ValidRanges [{:Min -100 :Max 1000} {:Min -10 :Max 0} {:Min 10 :Max 1000} {:Min 1000 :Max 100000}]}})

(def variable-missing-ValidRanges
  {:umm {:Dimensions [{:Size 200} {:Size 200}]
         :FillValues [{:Value 10} {:Value 1000}]}})

(def variable-missing-FillValues-And-ValidRanges
  {:umm {:Dimensions [{:Size 200} {:Size 200}]}})

(deftest get-netcdf3-measurement-1-byte
  (is (= 400
        (#'formats/get-netcdf3-measurement {:umm {:DataType "byte" :Dimensions [{:Size 10} {:Size 20}]}}))))

(deftest get-netcdf3-measurement-4-byte
  (is (= 800
        (#'formats/get-netcdf3-measurement {:umm {:DataType "float" :Dimensions [{:Size 10} {:Size 20}]}}))))

(deftest get-valid-ranges
  (is (=[{:Min -10 :Max 0} {:Min 0 :Max 10} {:Min 10 :Max 100}]
        (#'formats/get-valid-ranges {:umm {:ValidRanges [{:Min -10 :Max 10} {:Min 10 :Max 100}]}}))))

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
  (is (= 529992 (#'formats/estimate-ascii-size 2 [variable1 variable2] {:request-id 1234}))))
 
(deftest estimate-ascii-size-missing-Dimensions
  (is (= 0 (#'formats/estimate-ascii-size 2 [variable-missing-Dimensions] {:request-id 1234}))))

(deftest estimate-ascii-size-missing-FillValues
  ;; dimenstionality=40000. avg-valid-range-digit-number=(4+1+1+4+3+1+2+4+4+6)/10=3. 
  ;; the estimated size=40000 * 3 + 2 * 39999 = 199998.
  (is (= 199998 (#'formats/estimate-ascii-size 1 [variable-missing-FillValues] {:request-id 1234}))))

(deftest estimate-ascii-size-missing-ValidRanges 
  ;; dimenstionality=40000. avg-fill-value-digit-number=(2+4)/2=3
  ;; the estimated size=40000 * 3 + 2 * 39999 = 199998 
  (is (= 199998 (#'formats/estimate-ascii-size 1 [variable-missing-ValidRanges] {:request-id 1234}))))

(deftest estimate-ascii-size-missing-FillValues-And-ValidRanges
  ;; dimenstionality=40000. 
  ;; the estimated size=40000 * 3 + 2 * 39999 = 199998
  (is (= 199998 (#'formats/estimate-ascii-size 1 [variable-missing-FillValues-And-ValidRanges] {:request-id 1234}))))
