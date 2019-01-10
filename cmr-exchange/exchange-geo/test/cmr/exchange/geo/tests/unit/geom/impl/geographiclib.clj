(ns cmr.exchange.geo.tests.unit.geom.impl.geographiclib
  (:require
   [clojure.test :refer :all]
   [cmr.exchange.geo.geom.impl.geographiclib :as geographiclib]))

(deftest polygon
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1972221565152.8076
         (geographiclib/area
          (geographiclib/create
           [-49.0541474 -155.5668768
            -39.8347895 -156.6555975
            -38.9042264 -178.7459394
            -47.9997117 178.7260442
            -49.0541474 -155.5668768]))))
  ;; Polygon points taken from G134465696-NSIDC_ECS
  (is (= 1972217712438.969
         (geographiclib/area
          (geographiclib/create
           [-49.0541 -155.5669
            -39.8348 -156.6556
            -38.9042 -178.7459
            -47.9997 178.726
            -49.0541 -155.5669])))))
