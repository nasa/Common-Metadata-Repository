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
            [cmr.spatial.line :as l]
            [cmr.spatial.ring :as r]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.serialize :as srl]))

(defn round-ring
  [ring]
  (r/ring (map (partial p/round-point 7) (:points ring))))

(defn round-polygon
  [polygon]
  (poly/polygon (map round-ring (:rings polygon))))

(defspec ordinate-to-stored-test
  (for-all [d (gen/fmap double gen/ratio)]
    (let [rounded (round 7 d)]
      (= rounded (srl/stored->ordinate (srl/ordinate->stored rounded))))))

(deftest ordinate-to-stored-rounds-not-truncates-test
  (is (= 123.1234568 (srl/stored->ordinate (srl/ordinate->stored 123.12345675))))
  (is (= 123.1234567 (srl/stored->ordinate (srl/ordinate->stored 123.12345674)))))

(defspec polygon-ords-serialize-test 100
  ;; polygons with a single ring
  (for-all [polygon sgen/polygons-without-holes]
    (let [rounded-poly (round-polygon polygon)]
      (= rounded-poly
         (srl/stored-ords->shape
           :polygon
           (srl/shape->stored-ords rounded-poly))))))

