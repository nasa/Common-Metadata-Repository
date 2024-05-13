(ns cmr.spatial.test.vector
  (:refer-clojure :exclude [abs])
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.spatial.conversion :as c]
   [cmr.spatial.math :refer [abs approx=]]
   [cmr.spatial.point :as p]
   [cmr.spatial.test.generators :as sgen]
   [cmr.spatial.vector :as v]))

(deftest vector-equality-test
  (testing "long versus double"
    (is (= (v/new-vector 0 0 0)
           (v/new-vector 0.0 0.0 0.0)))))

(declare vector-lengths-spec)
(defspec vector-lengths-spec 100
  (for-all [value (gen/choose -10 10)]
    (every?
      (fn [field]
        (approx= (abs ^double value)
                 (v/length
                   (v/map->Vector
                     (assoc {:x 0.0 :y 0.0 :z 0.0} field (double value))))))
      [:x :y :z])))

(declare normalized-vectors-have-a-length-of-1)
(defspec normalized-vectors-have-a-length-of-1 100
  (for-all [v (gen/such-that (fn [v]
                               (> (v/length v) 0))
                             sgen/vectors)]
    (let [normalized (v/normalize v)
          length (v/length normalized)]
      (approx= 1.0 length))))

(declare parallel-vectors-spec)
(defspec parallel-vectors-spec 100
  (for-all [v sgen/vectors]
    (let [v (v/normalize v)
          opposite-vector (v/opposite v)
          vectors [v opposite-vector]]
      (every? #(apply v/parallel? %) (for [v1 vectors v2 vectors] [v1 v2])))))

(declare lon-lat-cross-product)
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

