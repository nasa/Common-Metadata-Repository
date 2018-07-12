(ns cmr.opendap.tests.unit.geom.core
  (:require
   [clojure.test :refer :all]
   [cmr.opendap.geom.core :as geom]
   [cmr.opendap.geom.util :as util]))

(deftest esri-polygon
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1.4313700783597213E13
         (geom/area
          (geom/create-polygon :esri
           [-49.0541474 -155.5668768
            -39.8347895 -156.6555975
            -38.9042264 -178.7459394
            -47.9997117 178.7260442
            -49.0541474 -155.5668768])))))

(deftest geographiclib-polygon
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1972221565152.8076
         (geom/area
          (geom/create-polygon :geographiclib
           [-49.0541474 -155.5668768
            -39.8347895 -156.6555975
            -38.9042264 -178.7459394
            -47.9997117 178.7260442
            -49.0541474 -155.5668768]))))
  ;; Polygon points taken from G134465696-NSIDC_ECS
  (is (= 1972217712438.969
         (geom/area
          (geom/create-polygon :geographiclib
           [-49.0541 -155.5669
            -39.8348 -156.6556
            -38.9042 -178.7459
            -47.9997 178.726
            -49.0541 -155.5669])))))

(deftest jts-polygon
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1.4313700783597215E13
         (geom/area
          (geom/create-polygon :jts
           [-49.0541474 -155.5668768
            -39.8347895 -156.6555975
            -38.9042264 -178.7459394
            -47.9997117 178.7260442
            -49.0541474 -155.5668768]))))
  (let [earth-poly (geom/create-polygon :jts
                    [-90 -180
                     90 -180
                     90 180
                     -90 180
                     -90 -180])
        subset-poly (geom/create-polygon :jts
                     [-15.1875 42.609375
                      -15.1875 25.3125
                      7.3125 25.3125
                      7.3125 42.609375
                      -15.1875 42.609375])]
  ;; Polygon for the whole earth
  (is (= util/earth-area
         (Math/floor
          (geom/area earth-poly))))
  ;; A subset of the earth
  (is (= 4780486324800.459 (geom/area subset-poly)))
  (is (= 4780486324800.459
        (geom/area (geom/intersection earth-poly subset-poly))))
  (is (= false (geom/empty? subset-poly)))
  (is (= true (geom/intersects? earth-poly subset-poly)))
  (is (= [[4743253.928019642 -1670935.6471926358 0.0]
          [2817774.6107047377 -1670935.6471926358 0.0]
          [2817774.6107047377 811815.6770162807 0.0]
          [4743253.928019642 811815.6770162807 0.0]
          [4743253.928019642 -1670935.6471926358 0.0]]
         (geom/points subset-poly)))
  (is (= 5 (geom/point-count subset-poly)))
  (is (= true (geom/valid? subset-poly)))))
