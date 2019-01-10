(ns cmr.ous.tests.unit.util.validation
  "Note: this namespace is exclusively for unit tests."
  (:require
   [clojure.test :refer :all]
   [cmr.ous.util.validation :as validation]))

(deftest check-latitude
  (is (validation/check-latitude [-90 90]))
  (is (validation/check-latitude [-45 45]))
  (is (validation/check-latitude [0 0]))
  (is (not (validation/check-latitude [-91 91])))
  (is (not (validation/check-latitude [-45 91])))
  (is (not (validation/check-latitude [-91 45]))))

(deftest check-longitude
  (is (validation/check-longitude [-180 180]))
  (is (validation/check-longitude [-90 90]))
  (is (validation/check-longitude [0 0]))
  (is (not (validation/check-longitude [-181 181])))
  (is (not (validation/check-longitude [-90 181])))
  (is (not (validation/check-longitude [-181 90]))))

(deftest validate-latitude
  (is (= [-90 90]
         (validation/validate-latitude [-90 90])))
  (is (= [-45 45]
         (validation/validate-latitude [-45 45])))
  (is (= [0 0]
         (validation/validate-latitude [0 0])))
  (is (= {:errors ["The values provided for latitude are not within the valid range of -90 degrees through 90 degrees."]}
         (validation/validate-latitude [-91 91])))
  (is (= {:errors ["The values provided for latitude are not within the valid range of -90 degrees through 90 degrees."]}
         (validation/validate-latitude [-45 91])))
  (is (= {:errors ["The values provided for latitude are not within the valid range of -90 degrees through 90 degrees."]}
         (validation/validate-latitude [-91 45]))))

(deftest validate-longitude
  (is (= [-180 180]
         (validation/validate-longitude [-180 180])))
  (is (= [-90 90]
         (validation/validate-longitude [-90 90])))
  (is (= [0 0]
         (validation/validate-longitude [0 0])))
  (is (= {:errors ["The values provided for longitude are not within the valid range of -180 degrees through 180 degrees."]}
         (validation/validate-longitude [-181 181])))
  (is (= {:errors ["The values provided for longitude are not within the valid range of -180 degrees through 180 degrees."]}
         (validation/validate-longitude [-90 181])))
  (is (= {:errors ["The values provided for longitude are not within the valid range of -180 degrees through 180 degrees."]}
         (validation/validate-longitude [-181 90]))))
