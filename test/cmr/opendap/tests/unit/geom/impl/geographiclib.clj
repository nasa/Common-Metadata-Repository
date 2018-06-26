(ns cmr.opendap.tests.unit.geom.impl.geographiclib
  (:require
   [clojure.test :refer :all]
   [cmr.opendap.geom.impl.geographiclib :as geographiclib]))

(deftest polygon-area
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1972221565152.8076
         (geographiclib/polygon-area
          [-49.0541474 -155.5668768
           -39.8347895 -156.6555975
           -38.9042264 -178.7459394
           -47.9997117 178.7260442
           -49.0541474 -155.5668768]))))
