(ns cmr.opendap.tests.unit.geom.impl.jts
  (:require
   [clojure.test :refer :all]
   [cmr.opendap.geom.impl.jts :as jts]))

(deftest polygon-area
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1.4313700783597215E13
         (jts/polygon-area
          [-49.0541474 -155.5668768
           -39.8347895 -156.6555975
           -38.9042264 -178.7459394
           -47.9997117 178.7260442
           -49.0541474 -155.5668768]))))
