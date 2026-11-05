(ns cmr.spatial.test.codec
  (:require
   [clojure.string :as string]
   [clojure.test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
   [cmr.spatial.codec :as c]
   [cmr.spatial.derived :as d]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.messages :as smesg]
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

      ",1,1,2,2"
      "1,1,2,2,"
      "NaN,1,2,2"
      "1e5,1,2,2"

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
      "1,1,2,2,3,3,1,1,"

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

(deftest url-decode-large-shapes-test
  (let [ords #(string/join "," (repeat (* 2 %) "-45.12345678"))]
    (testing "large shapes decode without overflowing the stack"
      (is (= (smesg/max-polygon-points)
             (count (:points (first (:rings (c/url-decode :polygon (ords (smesg/max-polygon-points)))))))))
      (is (= (smesg/max-line-points)
             (count (:points (c/url-decode :line (ords (smesg/max-line-points))))))))
    (testing "shapes over the max points"
      (are [type max-points]
           (let [s (ords (inc max-points))]
             (= {:errors [(smesg/line-too-many-points-msg type s)]}
                (c/url-decode type s)))
        :polygon (smesg/max-polygon-points)
        :line (smesg/max-line-points)))))

(declare point-encode-decode-test)
(defspec point-encode-decode-test 100
  (for-all [shape sgen/points]
    (= shape (c/url-decode :point (c/url-encode shape)))))

(declare polygon-encode-decode-test)
(defspec polygon-encode-decode-test 100
  ;; polygons with a single ring
  (for-all [shape sgen/geodetic-polygons-without-holes]
    (= shape (c/url-decode :polygon (c/url-encode shape)))))

(declare circle-encode-decode-test)
(defspec circle-encode-decode-test 100
  (for-all [shape sgen/circles]
    (= shape (c/url-decode :circle (c/url-encode shape)))))

(declare mbr-encode-decode-test)
(defspec mbr-encode-decode-test 100
  (for-all [shape sgen/mbrs]
    (= shape (c/url-decode :bounding-box (c/url-encode shape)))))

(declare line-encode-decode-test)
(defspec line-encode-decode-test 100
  (for-all [shape (gen/fmap #(l/set-coordinate-system % :geodetic)
                            sgen/lines)]
    (let [line (d/calculate-derived shape)]
      (= line (d/calculate-derived (c/url-decode :line (c/url-encode line)))))))




(comment
 (def l #cmr.spatial.line_string.LineString{:coordinate-system :geodetic, :points [#=(cmr.spatial.point/point -1.0 -1.0) #=(cmr.spatial.point/point -1.0 1.0)], :segments nil, :mbr nil})

 (c/url-decode :line (c/url-encode l)))
