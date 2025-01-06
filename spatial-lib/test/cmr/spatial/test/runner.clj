(ns cmr.spatial.test.runner-test
  "Run tests for functions defined in the runner for this project. These are small utility
   functions only."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.spatial.runner :as runner]))

(deftest parse-polygon-test
  (testing "parse-polygon function"
    (let [polygon-str "POLYGON((30.0 10.0, 40.0 40.0, 20.0 40.0, 10.0 20.0, 30.0 10.0))"]
      (is (= [30.0 10.0 40.0 40.0 20.0 40.0 10.0 20.0 30.0 10.0]
             (runner/parse-polygon polygon-str))
          "Should correctly parse a valid polygon string"))

    (let [polygon-str "POLYGON((-73.935242 40.730610, -73.935242 40.730610, -73.934058 40.731081, -73.933489 40.730131, -73.934703 40.729661, -73.935242 40.730610))"]
      (is (= [-73.935242 40.730610 -73.935242 40.730610 -73.934058 40.731081 -73.933489 40.730131 -73.934703 40.729661 -73.935242 40.730610]
             (runner/parse-polygon polygon-str))
          "Should correctly parse a polygon string with negative coordinates"))

    (let [polygon-str "POLYGON((0.0 0.0, 1.0 1.0, 2.0 2.0))"]
      (is (= [0.0 0.0 1.0 1.0 2.0 2.0]
             (runner/parse-polygon polygon-str))
          "Should correctly parse a polygon string with fewer points"))

    (let [polygon-str "POLYGON(())"]
      (is (= []
             (runner/parse-polygon polygon-str))
          "Should return an empty vector for an empty polygon"))

    (let [polygon-str "NOT A POLYGON"]
      (is (= []
             (runner/parse-polygon polygon-str))
          "Should return an empty vector for an invalid polygon string"))))

(deftest test-create-wkt-bbox
  (testing "create-wkt-bbox with map input"
    (let [input {:west -10.5, :east 10.5, :south -5.25, :north 5.25}
          expected "POLYGON((-10.5 -5.25, 10.5 -5.25, 10.5 5.25, -10.5 5.25, -10.5 -5.25))"]
      (is (= expected (runner/create-wkt-bbox input)))))

  (testing "create-wkt-bbox with individual coordinate inputs"
    (let [west -10.5
          east 10.5
          south -5.25
          north 5.25
          expected "POLYGON((-10.5 -5.25, 10.5 -5.25, 10.5 5.25, -10.5 5.25, -10.5 -5.25))"]
      (is (= expected (runner/create-wkt-bbox west east south north)))))

  (testing "create-wkt-bbox with integer coordinates"
    (let [input {:west -10, :east 10, :south -5, :north 5}
          expected "POLYGON((-10 -5, 10 -5, 10 5, -10 5, -10 -5))"]
      (is (= expected (runner/create-wkt-bbox input)))))

  (testing "create-wkt-bbox with zero coordinates"
    (let [input {:west 0, :east 0, :south 0, :north 0}
          expected "POLYGON((0 0, 0 0, 0 0, 0 0, 0 0))"]
      (is (= expected (runner/create-wkt-bbox input))))))

(deftest test-polygon-string->box
  (testing "polygon-string->box function"
    (let [polygon-str "POLYGON((30.0 10.0, 40.0 40.0, 20.0 40.0, 10.0 20.0, 30.0 10.0))"
          expected-result {:west 16.767578125
                           :east 32.50732421875
                           :south 17.613525390625
                           :north 33.44390869140625}
          actual-result (runner/polygon-string->box polygon-str)]
      (is (= expected-result actual-result) "The bounding box should match the expected values")

      (is (number? (:west actual-result)) "West should be a number")
      (is (number? (:east actual-result)) "East should be a number")
      (is (number? (:south actual-result)) "South should be a number")
      (is (number? (:north actual-result)) "North should be a number")

      (is (<= (:west actual-result) (:east actual-result)) "West should be less than or equal to East")
      (is (<= (:south actual-result) (:north actual-result)) "South should be less than or equal to North"))))

(deftest test-polygon-from-file->box
  (testing "Testing really big polygon from a file"
    (let [polygon-str (slurp (io/resource "multipolygon.txt"))
          polygon-box (runner/polygon-string->box polygon-str)]
      (util/are3
       [expected given]
       (is (= expected (polygon-box given))
           (format "polygon-box field %s did not match" given))

       "- west" -121.02395663928222 :west
       "- east" -118.47151932092285 :east
       "- south" 35.501922038989605 :south
       "- north" 37.9313141796875 :north))))
