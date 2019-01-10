(ns cmr.exchange.geo.tests.unit.geom.impl.esri
  (:require
   [clojure.test :refer :all]
   [cmr.exchange.geo.geom.impl.esri :as esri]))

(deftest polygon
  ;; Polygon points taken from G1344353303-NSIDC_ECS
  (is (= 1.4313700783597213E13
         (esri/area
          (esri/create
           [-49.0541474 -155.5668768
            -39.8347895 -156.6555975
            -38.9042264 -178.7459394
            -47.9997117 178.7260442
            -49.0541474 -155.5668768])))))
