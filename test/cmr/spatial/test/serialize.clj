(ns cmr.spatial.test.serialize
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.serialize :as srl])
  (:import cmr.spatial.geodetic_ring.GeodeticRing
           cmr.spatial.cartesian_ring.CartesianRing
           cmr.spatial.polygon.Polygon
           cmr.spatial.point.Point
           cmr.spatial.mbr.Mbr
           cmr.spatial.line_string.LineString))

(defmulti round-shape
  "Rounds the shape as it would be rounded when stored so that we can compare items"
  (fn [shape]
    (type shape)))

(defmethod round-shape Mbr
  [mbr]
  (let [r (partial round 7)
        {w :west n :north e :east s :south} mbr]
    (m/mbr (r w) (r n) (r e) (r s))))

(defmethod round-shape Point
  [point]
  (p/round-point 7 point))

(defmethod round-shape LineString
  [line]
  (l/line-string (:coordinate-system line) (map (partial p/round-point 7) (:points line))))

(defmethod round-shape GeodeticRing
  [ring]
  (gr/ring (map (partial p/round-point 7) (:points ring))))

(defmethod round-shape CartesianRing
  [ring]
  (cr/ring (map (partial p/round-point 7) (:points ring))))

(defmethod round-shape Polygon
  [polygon]
  (poly/polygon (:coordinate-system polygon) (map round-shape (:rings polygon))))

(defspec ordinate-to-stored-test
  (for-all [d (gen/fmap double gen/ratio)]
    (let [rounded (round 7 d)]
      (= rounded (srl/stored->ordinate (srl/ordinate->stored rounded))))))

(deftest ordinate-to-stored-rounds-not-truncates-test
  (is (= 123.1234568 (srl/stored->ordinate (srl/ordinate->stored 123.12345675))))
  (is (= 123.1234567 (srl/stored->ordinate (srl/ordinate->stored 123.12345674)))))

(defn print-failed-shapes
  [type shapes]
  (doseq [shape shapes]
    (sgen/print-failed-polygon type shape)))

(defspec ords-serialize-test {:times 100}
  (for-all [shapes (gen/fmap #(map round-shape %)
                             (gen/vector sgen/geometries 1 5))]
    (let [ords-map (srl/shapes->ords-info-map shapes)
          {:keys [ords ords-info]} ords-map
          parsed-shapes (srl/ords-info->shapes ords-info ords)]
      (= shapes parsed-shapes))))

