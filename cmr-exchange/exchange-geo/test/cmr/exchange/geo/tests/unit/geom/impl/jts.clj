(ns cmr.exchange.geo.tests.unit.geom.impl.jts
  (:require
   [clojure.test :refer :all]
   [cmr.exchange.geo.geom.impl.jts :as jts]
   [cmr.exchange.geo.geom.util :as util]))

(deftest polygon
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1.4313700783597215E13
         (jts/area
          (jts/create
           [-49.0541474 -155.5668768
            -39.8347895 -156.6555975
            -38.9042264 -178.7459394
            -47.9997117 178.7260442
            -49.0541474 -155.5668768]))))
  (let [earth-poly (jts/create
                    [-90 -180
                     90 -180
                     90 180
                     -90 180
                     -90 -180])
        subset-poly (jts/create
                     [-15.1875 42.609375
                      -15.1875 25.3125
                      7.3125 25.3125
                      7.3125 42.609375
                      -15.1875 42.609375])]
  ;; Polygon for the whole earth
  (is (= util/earth-area
         (Math/floor
          (jts/area earth-poly))))
  ;; A subset of the earth
  (is (= 4780486324800.459 (jts/area subset-poly)))
  (is (= 4780486324800.459
        (jts/area (jts/intersection earth-poly subset-poly))))
  (is (= false (jts/empty? subset-poly)))
  (is (= true (jts/intersects? earth-poly subset-poly)))
  (is (= [[4743253.928019642 -1670935.6471926358 0.0]
          [2817774.6107047377 -1670935.6471926358 0.0]
          [2817774.6107047377 811815.6770162807 0.0]
          [4743253.928019642 811815.6770162807 0.0]
          [4743253.928019642 -1670935.6471926358 0.0]]
         (jts/points subset-poly)))
  (is (= 5 (jts/point-count subset-poly)))
  (is (= true (jts/valid? subset-poly)))))
