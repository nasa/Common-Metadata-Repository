(ns cmr.spatial.test.conversion
  (:require
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.spatial.conversion :as c]
   [cmr.spatial.math :refer [approx=]]
   [cmr.spatial.point :as p]
   [cmr.spatial.test.generators :as sgen]
   [cmr.spatial.vector :as v]))

(declare lon-lat-cross-product-test)
(defspec lon-lat-cross-product-test 100
  (for-all [p1 sgen/points
            p2 sgen/points]
    (let [v1 (c/point->vector p1)
          v2 (c/point->vector p2)
          cp1 (c/vector->point (c/lon-lat-cross-product p1 p2))
          cpv (c/vector->point (v/cross-product v1 v2))
          cp2 (c/vector->point (c/lon-lat-cross-product p2 p1))]
      (and
        (or (approx= cp1 cp2)
            (approx= cp1 (p/antipodal cp2)))

        ;; The lon lat cross product is approximately equal to the cross product using vectors directly
        (or (approx= cp1 cpv)
            (approx= cp1 (p/antipodal cpv)))))))

(declare point->vector-conversion)
(defspec point->vector-conversion 100
  (for-all
    [point sgen/points]
    (approx= point (c/vector->point
                     (c/point->vector point)))))

(declare vector->point-conversion)
(defspec vector->point-conversion 100
  (for-all
    [v sgen/vectors]
    (approx= (v/normalize v)
             (c/point->vector
               (c/vector->point v)))))
