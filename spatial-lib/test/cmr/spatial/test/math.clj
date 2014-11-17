(ns cmr.spatial.test.math
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :as gen-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;;my code
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]))

(primitive-math/use-primitive-operators)

(defn math-values-close?
  "Helper for testing math value accuracy"
  [^double v1 ^double v2]
  (or (and (Double/isNaN v1)
           (Double/isNaN v2))
      (< (Math/abs (- v1 v2)) 0.000000000001)))

(def doubles-gen
  (gen/fmap double gen/ratio))

;; Tests the accuracy of the math functions provided by the Jafama library. It ensures they are up
;; to par with the Java Math.
(defspec test-math-accuracy 2000
  (for-all [dvalue doubles-gen]
    (let [^double d dvalue]
      (and
        (math-values-close? (Math/cos d) (cos d))
        (math-values-close? (Math/sin d) (sin d))
        (math-values-close? (Math/acos d) (acos d))
        (math-values-close? (Math/asin d) (asin d))
        (math-values-close? (Math/tan d) (tan d))
        (math-values-close? (Math/atan d) (atan d))
        (math-values-close? (Math/abs d) (abs d))
        (math-values-close? (Math/sqrt d) (sqrt d))))))

(defspec test-math-atan2-accuracy 2000
  (for-all [dvalue1 doubles-gen
            dvalue2 doubles-gen]
    (let [^double d1 dvalue1
          ^double d2 dvalue2]
      (math-values-close? (Math/atan2 d1 d2) (atan2 d1 d2)))))

(defspec double-to-float-round-up-spec 2000
  (for-all [^double dvalue doubles-gen]
    (let [fvalue (double->float dvalue true)
          dvalue2 (float->double fvalue)]
      (and (float-type? fvalue)
           (>= dvalue2 dvalue)))))

(defspec double-to-float-round-down-spec 2000
  (for-all [^double dvalue doubles-gen]
    (let [fvalue (double->float dvalue false)
          dvalue2 (float->double fvalue)]
      (and (float-type? fvalue)
           (<= dvalue2 dvalue)))))

(defspec round-spec 2000
  (for-all [precision (gen/choose 0 10)
            n (gen-ext/choose-double -100 100)]
    (= (Double. (format (str "%." precision "f") n))
       (round precision n))))

(defspec radians-degrees-spec 100
  (for-all [d (gen-ext/choose-double -360 360)]
    (let [r (radians d)
          d2 (degrees r)]
      (and
        ;; Converting to radians then back to degrees should match
        (approx= d d2)

        ;; Radians should be within -2 PI and +2 PI
        (within-range? r (* -2.0 PI) (* 2.0 PI))))))

(deftest approx-equal-test
  (testing "changing the delta"
    (is (approx= 0.0001 0.0002 0.0001))
    (is (approx= 0.0001 0.0002 0.001))
    (is (not (approx= 0.0001 0.00021 0.0001)))
    (is (approx= 0.0001 0.00021 0.001)))
  (testing "vectors"
    (is (approx= [1] [1]))
    (is (approx= [1] [1.0]))
    (is (not (approx= [1] [1 2])))
    (is (not (approx= [1] []))))
  (testing "vector and lazy sequence"
    (let [items [1 2 3.0000001]]
      (is (approx= items (map identity items)))
      (is (approx= (map identity items) items))))
  (testing "vectors and lists"
    (is (approx= [1 2 3] '(1 2 3.0000001)))
    (is (not (approx= [1 2 3] '(1 3.0000001 2))) "order matters")
    (is (not (approx= [1 2 3] '(1 2 3.1)))))

  (testing "maps and sequences"
    (is (not (approx= {} [])))
    (is (not (approx= [] {})))))

(defspec antipodal-lon-spec 100
  (for-all [lon sgen/lons]
    (let [lon 76.85714285714286
          opposite-lon (antipodal-lon lon)]
      (and (within-range? opposite-lon -180 180)
           (approx= (+ (abs opposite-lon) (abs lon)) 180.0)))))

