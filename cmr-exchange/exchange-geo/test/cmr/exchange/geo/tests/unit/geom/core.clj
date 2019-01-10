(ns cmr.exchange.geo.tests.unit.geom.core
  (:require
   [clojure.test :refer :all]
   [cmr.exchange.geo.geom.core :as geom]
   [cmr.exchange.geo.geom.util :as util]))

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
                     [-15.46875 43.03125
                      -15.46875 30.375
                      4.359375 30.375
                      4.359375 43.03125
                      -15.46875 43.03125])]
  ;; Polygon for the whole earth
  (is (= util/earth-area
         (Math/floor
          (geom/area earth-poly))))
  ;; A subset of the earth
  (is (= 3.079751330475716E12 (geom/area subset-poly)))
  (is (= 3.079751330475716E12
        (geom/area (geom/intersection earth-poly subset-poly))))
  (is (= false (geom/empty? subset-poly)))
  (is (= true (geom/intersects? earth-poly subset-poly)))
  (is (= [[4790216.838198054 -1701130.506822676 0.0]
          [3381329.5328456853 -1701130.506822676 0.0]
          [3381329.5328456853 484815.32346178207 0.0]
          [4790216.838198054 484815.32346178207 0.0]
          [4790216.838198054 -1701130.506822676 0.0]]
         (geom/points subset-poly)))
  (is (= 5 (geom/point-count subset-poly)))
  (is (= true (geom/valid? subset-poly)))))
