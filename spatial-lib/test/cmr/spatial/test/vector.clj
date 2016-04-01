(ns cmr.spatial.test.vector
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;;my code
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.vector :as v]
            [cmr.spatial.point :as p]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.math :refer :all]))

(deftest vector-equality-test
  (testing "long versus double"
    (is (= (v/new-vector 0 0 0)
           (v/new-vector 0.0 0.0 0.0)))))

(defspec vector-lengths-spec 100
  (for-all [value (gen/choose -10 10)]
    (every?
      (fn [field]
        (approx= (abs ^double value)
                 (v/length
                   (v/map->Vector
                     (assoc {:x 0.0 :y 0.0 :z 0.0} field (double value))))))
      [:x :y :z])))

(defspec normalized-vectors-have-a-length-of-1 100
  (for-all [v (gen/such-that (fn [v]
                               (> (v/length v) 0))
                             sgen/vectors)]
    (let [normalized (v/normalize v)
          length (v/length normalized)]
      (approx= 1.0 length))))

(defspec parallel-vectors-spec 100
  (for-all [v sgen/vectors]
    (let [v (v/normalize v)
          opposite-vector (v/opposite v)
          vectors [v opposite-vector]]
      (every? #(apply v/parallel? %) (for [v1 vectors v2 vectors] [v1 v2])))))

(defspec lon-lat-cross-product 100
  (for-all [p1 sgen/points
            p2 sgen/points]
    (let [v1 (c/point->vector p1)
          v2 (c/point->vector p2)
          ;; Assertions should throw an exception if the point is invalid
          cp1 (c/vector->point (v/cross-product v1 v2))
          cp2 (c/vector->point (v/cross-product v2 v1))]
      (or (approx= cp1 (p/antipodal cp2) 0.0001)
          (approx= cp1 cp2 0.0001)))))

