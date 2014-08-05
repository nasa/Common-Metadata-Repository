(ns cmr.system-int-test.search.collection-spatial-search-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec :as codec]
            [clojure.string :as str]
            [cmr.spatial.dev.viz-helper :as viz-helper]
            [cmr.spatial.serialize :as srl]
            [cmr.common.dev.util :as dev-util]
            [cmr.spatial.lr-binary-search :as lbs]
            [cmr.umm.spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order.
  The polygon will be closed automatically."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn search-poly
  "Returns a url encoded polygon for searching"
  [& ords]
  (codec/url-encode (umm-s/set-coordinate-system :geodetic (apply polygon ords))))

(deftest spatial-search-test
  (let [make-coll (fn [coord-sys et & shapes]
                    (d/ingest "PROV1"
                              (dc/collection
                                {:entry-title et
                                 :spatial-coverage (apply dc/spatial
                                                          coord-sys
                                                          coord-sys
                                                          shapes)})))

        ;; Bounding rectangles
        whole-world (make-coll :geodetic "whole-world" (m/mbr -180 90 180 -90))
        touches-np (make-coll :geodetic "touches-np" (m/mbr 45 90 55 70))
        touches-sp (make-coll :geodetic "touches-sp" (m/mbr -160 -70 -150 -90))
        across-am-br (make-coll :geodetic "across-am-br" (m/mbr 170 10 -170 -10))
        normal-brs (make-coll :geodetic "normal-brs"
                              (m/mbr 10 10 20 0)
                              (m/mbr -20 0 -10 -10))

        ;; Polygons
        wide-north (make-coll :geodetic "wide-north" (polygon -70 20, 70 20, 70 30, -70 30, -70 20))
        wide-south (make-coll :geodetic "wide-south" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        across-am-poly (make-coll :geodetic "across-am-poly" (polygon 170 35, -175 35, -170 45, 175 45, 170 35))
        on-np (make-coll :geodetic "on-np" (polygon 45 85, 135 85, -135 85, -45 85, 45 85))
        on-sp (make-coll :geodetic "on-sp" (polygon -45 -85, -135 -85, 135 -85, 45 -85, -45 -85))
        normal-poly (make-coll :geodetic "normal-poly" (polygon -20 -10, -10 -10, -10 10, -20 10, -20 -10))

        ;; polygon with holes
        outer (umm-s/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (umm-s/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (umm-s/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (make-coll :geodetic "polygon-with-holes" (poly/polygon [outer hole1 hole2]))

        ;; Cartesian Polygons
        wide-north-cart (make-coll :cartesian "wide-north-cart" (polygon -70 20, 70 20, 70 30, -70 30, -70 20))
        wide-south-cart (make-coll :cartesian "wide-south-cart" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        very-wide-cart (make-coll :cartesian "very-wide-cart" (polygon -180 40, -180 35, 180 35, 180 40, -180 40))
        very-tall-cart (make-coll :cartesian "very-tall-cart" (polygon -160 90, -160 -90, -150 -90, -150 90, -160 90))
        normal-poly-cart (make-coll :cartesian "normal-poly-cart" (polygon 1.534 -16.52, 6.735 -14.102, 3.745 -9.735, -1.454 -11.802, 1.534 -16.52))

        ;; Points
        north-pole (make-coll :geodetic "north-pole" (p/point 0 90))
        south-pole (make-coll :geodetic "south-pole" (p/point 0 -90))
        normal-point (make-coll :geodetic "normal-point" (p/point 10 22))
        am-point (make-coll :geodetic "am-point" (p/point 180 22))]
    (index/refresh-elastic-index)

    (testing "point searches"
      (are [lon_lat items]
           (let [found (search/find-refs :collection {:point (codec/url-encode (apply p/point lon_lat))
                                                   :page-size 50})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :entry-title) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           ;; north pole
           [0 90] [whole-world north-pole on-np touches-np]

           ;; south pole
           [0 -90] [whole-world south-pole on-sp touches-sp]

           ;; in hole of polygon with a hole
           [4.83 1.06] [whole-world]
           ;; in hole of polygon with a hole
           [1.67 5.43] [whole-world]
           ;; and not in hole
           [1.95 3.36] [whole-world polygon-with-holes]

           ;; in mbr
           [17.73 2.21] [whole-world normal-brs]

           ;;matches exact point on polygon
           [-5.26 -2.59] [whole-world polygon-with-holes]

           ;; Matches a point
           [10 22] [whole-world normal-point wide-north-cart]

           [-154.821 37.84] [whole-world very-wide-cart very-tall-cart]

           ;; Near but not inside the cartesian normal polygon
           [-2.212,-12.44] [whole-world]
           [0.103,-15.911] [whole-world]
           ;; inside the cartesian normal polygon
           [2.185,-11.161] [whole-world normal-poly-cart]))

    (testing "bounding rectangle searches"
      (are [wnes items]
           (let [found (search/find-refs :collection {:bounding-box (codec/url-encode (apply m/mbr wnes))
                                                   :page-size 50})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :entry-title) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           [-23.43 5 25.54 -6.31] [whole-world polygon-with-holes normal-poly normal-brs]

           ;; inside polygon with hole
           [4.03,1.51,4.62,0.92] [whole-world]
           ;; corner points inside different holes
           [4.03,5.94,4.35,0.92] [whole-world polygon-with-holes]

           ;; just under wide north polygon
           [-1.82,46.56,5.25,44.04] [whole-world]
           [-1.74,46.98,5.25,44.04] [whole-world wide-north]
           [-1.74 47.05 5.27 44.04] [whole-world wide-north]

           ;; vertical slice of earth
           [-10 90 10 -90] [whole-world on-np on-sp wide-north wide-south polygon-with-holes
                            normal-poly normal-brs north-pole south-pole normal-point
                            very-wide-cart wide-north-cart wide-south-cart normal-poly-cart]

           ;; crosses am
           [166.11,53.04,-166.52,-19.14] [whole-world across-am-poly across-am-br am-point very-wide-cart]

           ;; whole world
           [-180 90 180 -90] [whole-world touches-np touches-sp across-am-br normal-brs
                              wide-north wide-south across-am-poly on-sp on-np normal-poly
                              polygon-with-holes north-pole south-pole normal-point am-point
                              very-wide-cart very-tall-cart wide-north-cart wide-south-cart
                              normal-poly-cart]))

    (testing "polygon searches"
      (are [ords items]
           (let [found (search/find-refs :collection {:polygon (apply search-poly ords) })
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :entry-title) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           [20.16,-13.7,21.64,12.43,12.47,11.84,-22.57,7.06,20.16,-13.7]
           [whole-world normal-poly normal-brs polygon-with-holes]

           ;; Intersects 2nd of normal-brs
           [-16.79,-12.71,-6.32,-10.95,-5.74,-6.11,-15.18,-7.63,-16.79,-12.71]
           [whole-world normal-poly normal-brs]

           [0.53,39.23,21.57,59.8,-112.21,84.48,-13.37,40.91,0.53,39.23]
           [whole-world on-np wide-north very-wide-cart]

           ;; around north pole
           [58.41,76.95,163.98,80.56,-122.99,81.94,-26.18,82.82,58.41,76.95]
           [whole-world on-np touches-np north-pole very-tall-cart]

           ;; around south pole
           [-161.53,-69.93,25.43,-51.08,13.89,-39.94,-2.02,-40.67,-161.53,-69.93]
           [whole-world on-sp wide-south touches-sp south-pole very-tall-cart]

           ;; Across antimeridian
           [-163.9,49.6,171.51,53.82,166.96,-11.32,-168.36,-14.86,-163.9,49.6]
           [whole-world across-am-poly across-am-br am-point very-wide-cart]

           [-2.212 -12.44, 0.103 -15.911, 2.185 -11.161 -2.212 -12.44]
           [whole-world normal-poly-cart]

           ;; Related the polygon with the hole
           ;; Inside holes
           [4.1,0.64,4.95,0.97,6.06,1.76,3.8,1.5,4.1,0.64] [whole-world]
           [1.41,5.12,3.49,5.52,2.66,6.11,0.13,6.23,1.41,5.12] [whole-world]
           ;; Partially inside a hole
           [3.58,-1.34,4.95,0.97,6.06,1.76,3.8,1.5,3.58,-1.34] [whole-world polygon-with-holes]
           ;; Covers a hole
           [3.58,-1.34,5.6,0.05,7.6,2.33,2.41,2.92,3.58,-1.34] [whole-world polygon-with-holes]
           ;; points inside both holes
           [4.44,0.66,5.4,1.35,2.66,6.11,0.13,6.23,4.44,0.66] [whole-world polygon-with-holes]
           ;; completely covers the polygon with holes
           [-6.45,-3.74,12.34,-4.18,12,9.45,-6.69,9.2,-6.45,-3.74] [whole-world polygon-with-holes normal-brs]))))


