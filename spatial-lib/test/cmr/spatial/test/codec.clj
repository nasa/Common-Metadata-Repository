(ns cmr.spatial.test.codec
  (:require
   [clojure.test :refer :all]

   ; [clojure.test.check.clojure-test :refer [defspec]]
   ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
   [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]

   [clojure.test.check.properties :refer [for-all]]
   [clojure.test.check.generators :as gen]
   ;; my code
   [cmr.spatial.codec :as c]
   [cmr.spatial.derived :as d]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.messages :as smesg]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.test.generators :as sgen]))

(deftest url-decode-test
  (testing "invalid points"
    (are [s] (= {:errors [(smesg/shape-decode-msg :point s)]}
                (c/url-decode :point s))
      "foo"
      "45"
      "45,,45"
      "45.045"
      "45,a"
      "45,45,"))
  (testing "invalid lines"
    (are [s] (= {:errors [(smesg/shape-decode-msg :line s)]}
                (c/url-decode :line s))
      "foo"
      "45"
      "45,,45"
      "45.045"
      "45,a"
      "45,45,"

      ;; too few points
      "1,1"
      ;; odd number of ordinates
      "1,1,2,2,3"))
  (testing "invalid polygons"
    (are [s] (= {:errors [(smesg/shape-decode-msg :polygon s)]}
                (c/url-decode :polygon s))
      "foo"
      "45"
      "45,,45"
      "45.045"
      "45,a"
      "45,45,"

      ;; too few points
      "1,1,2,2,3,3"
      "1,1,2,2,3,3,4"
      "1,1,2,2,3,3,4,"

      ;; odd number of ordinates
      "1,1,2,2,3,3,4,4,5"))
  (testing "invalid mbrs"
    (are [s] (= {:errors [(smesg/shape-decode-msg "bounding_box" s)]}
                (c/url-decode :bounding-box s))
      "foo"
      "45,,45"
      "1,1,1,a"
      "1,1,1,1,a"

      ;; too few coordinates
      "1"
      "1,2"
      "1,2,3"
      "1,2,3,4,"
      ;; Too many
      "1,2,3,4,5"))

  (testing "invalid circles"
    (are [s] (= {:errors [(smesg/shape-decode-msg "circle" s)]}
                (c/url-decode :circle s))
      "foo"
      "45,,45"
      "1,1,a"

      ;; too few coordinates
      "1"
      "1,2"
      ;; Too many
      "1,2,3,"
      "1,2,3,4")))

(defspec point-encode-decode-test 100
  (for-all [shape sgen/points]
    (= shape (c/url-decode :point (c/url-encode shape)))))

(defspec polygon-encode-decode-test 100
  ;; polygons with a single ring
  (for-all [shape sgen/geodetic-polygons-without-holes]
    (= shape (c/url-decode :polygon (c/url-encode shape)))))

(defspec circle-encode-decode-test 100
  (for-all [shape sgen/circles]
    (= shape (c/url-decode :circle (c/url-encode shape)))))

(defspec mbr-encode-decode-test 100
  (for-all [shape sgen/mbrs]
    (= shape (c/url-decode :bounding-box (c/url-encode shape)))))

(defspec line-encode-decode-test 100
  (for-all [shape (gen/fmap #(l/set-coordinate-system % :geodetic)
                            sgen/lines)]
    (let [line (d/calculate-derived shape)]
      (= line (d/calculate-derived (c/url-decode :line (c/url-encode line)))))))




(comment
 (def l #cmr.spatial.line_string.LineString{:coordinate-system :geodetic, :points [#=(cmr.spatial.point/point -1.0 -1.0) #=(cmr.spatial.point/point -1.0 1.0)], :segments nil, :mbr nil})

 (c/url-decode :line (c/url-encode l)))
