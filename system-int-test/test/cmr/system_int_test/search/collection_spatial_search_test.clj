(ns cmr.system-int-test.search.collection-spatial-search-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :as u :refer [are3]]
   [cmr.spatial.codec :as codec]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.messages :as smsg]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.umm-spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise
  order."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn search-poly
  "Returns a url encoded polygon for searching"
  [& ords]
  (codec/url-encode (umm-s/set-coordinate-system :geodetic (apply polygon ords))))

(defn make-coll
  [coord-sys et & shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (d/ingest "PROV1"
              (dc/collection
                {:entry-title et
                 :spatial-coverage (dc/spatial {:gsr coord-sys
                                                :sr coord-sys
                                                :geometries shapes})}))))

(deftest excessive-points-line-spatial-search-test
  (let [whole-world (make-coll :geodetic "whole-world" (m/mbr -180 90 180 -90))
        excessive-amount-of-points (slurp (io/resource "large-line-param.txt"))]
    (index/wait-until-indexed)
    (testing "Excessive amount of points"
      (testing "line search"
        (let [found (search/find-refs :collection
                                      {:line excessive-amount-of-points
                                       :provider "PROV1"
                                       :page-size 50})]
          (is (d/refs-match? [whole-world] found))))
      (testing "invalid lines"
        (is (= {:errors [(smsg/duplicate-points [[0 (p/point -17 -34)] [1 (p/point -17 -34)]])] :status 400}
               (search/find-refs :collection
                                 {:line (str "-17.0,-34.0," excessive-amount-of-points) :provider "PROV1"}))))
      (testing "invalid encoding"
        (let [points (search/make-excessive-points-without-dups 498)]
          (is (= {:errors [(smsg/shape-decode-msg :line (str "foo," points ",bar"))]
                  :status 400}
                 (search/find-refs :collection
                                   {:line (str "foo," points ",bar") :provider "PROV1"})))))
      (testing "too many points"
        (let [points (search/make-excessive-points-without-dups 520)]
          (is (= {:errors [(smsg/line-too-many-points-msg :line points)]
                  :status 400}
                 (search/find-refs :collection {:line points :provider "PROV1"}))))))))

(deftest spatial-search-test
  (let [;; Lines
        normal-line (make-coll :geodetic "normal-line"
                               (l/ords->line-string :geodetic [22.681 -8.839, 18.309 -11.426, 22.705 -6.557]))
        along-am-line (make-coll :geodetic "along-am-line"
                                 (l/ords->line-string :geodetic [-180 0 -180 85]))
        normal-line-cart (make-coll :cartesian "normal-line-cart"
                                    (l/ords->line-string :cartesian [16.439 -13.463,  31.904 -13.607, 31.958 -10.401]))

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

        outer-cart (umm-s/ords->ring -5.26 -22.59 11.56 -22.77 10.47 -11.29 -5.86 -11.37 -5.26 -22.59)
        hole1-cart (umm-s/ords->ring 6.95 -17.95 2.98 -17.94 3.92 -20.08 6.95 -17.95)
        hole2-cart (umm-s/ords->ring 5.18 -13.08 -1.79 -12.99 -2.65 -15 4.29 -14.95 5.18 -13.08)
        polygon-with-holes-cart (make-coll :cartesian "polygon-with-holes-cart" (poly/polygon [outer-cart hole1-cart hole2-cart]))

        ;; Points
        north-pole (make-coll :geodetic "north-pole" (p/point 0 90))
        south-pole (make-coll :geodetic "south-pole" (p/point 0 -90))
        normal-point (make-coll :geodetic "normal-point" (p/point 10 22))
        am-point (make-coll :geodetic "am-point" (p/point 180 22))
        all-colls [whole-world touches-np touches-sp across-am-br normal-brs wide-north wide-south
                   across-am-poly on-sp on-np normal-poly polygon-with-holes north-pole south-pole
                   normal-point am-point very-wide-cart very-tall-cart wide-north-cart
                   wide-south-cart normal-poly-cart polygon-with-holes-cart normal-line
                   normal-line-cart along-am-line]]
    (index/wait-until-indexed)

    (testing "line searches"
      (are [ords items]
        (let [found (search/find-refs
                     :collection
                     {:line (codec/url-encode (l/ords->line-string :geodetic ords))
                      :page-size 50})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :entry-title) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        ;; normal two points
        [-24.28,-12.76,10,10] [whole-world polygon-with-holes normal-poly normal-brs]

        ;; normal multiple points
        [-0.37,-14.07,4.75,1.27,25.13,-15.51] [whole-world polygon-with-holes
                                               polygon-with-holes-cart normal-line-cart
                                               normal-line normal-poly-cart]
        ;; across antimeridian
        [-167.85,-9.08,171.69,43.24] [whole-world across-am-br across-am-poly very-wide-cart
                                      along-am-line]

        ;; across north pole
        [0 85, 180 85] [whole-world north-pole on-np touches-np along-am-line]

        ;; across north pole where cartesian polygon touches it
        [-155 85, 25 85] [whole-world north-pole on-np very-tall-cart]

        ;; across south pole
        [0 -85, 180 -85] [whole-world south-pole on-sp]

        ;; across north pole where cartesian polygon touches it
        [-155 -85, 25 -85] [whole-world south-pole on-sp touches-sp very-tall-cart]))

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

        ;; Matches a granule point
        [10 22] [whole-world normal-point wide-north-cart]

        [-154.821 37.84] [whole-world very-wide-cart very-tall-cart]

        ;; Near but not inside the cartesian normal polygon
        ;; and also insid the polygon with holes (outside the holes)
        [-2.212,-12.44] [whole-world polygon-with-holes-cart]
        [0.103,-15.911] [whole-world polygon-with-holes-cart]
        ;; inside the cartesian normal polygon
        [2.185,-11.161] [whole-world normal-poly-cart]

        ;; inside a hole in the cartesian polygon
        [4.496,-18.521] [whole-world]

        ;; point on geodetic line
        [20.0 -10.437310310746927] [whole-world normal-line]
        ;; point on cartesian line
        [20.0 -13.496157710960231] [whole-world normal-line-cart]))

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

        ;; inside hole in geodetic
        [4.03,1.51,4.62,0.92] [whole-world]
        ;; corner points inside different holes
        [4.03,5.94,4.35,0.92] [whole-world polygon-with-holes]

        ;; inside hole in cartesian polygon
        [-0.54,-13.7,3.37,-14.45] [whole-world normal-poly-cart]
        ;; inside different holes in cartesian polygon
        [3.57,-14.38,3.84,-18.63] [whole-world normal-poly-cart polygon-with-holes-cart]

        ;; just under wide north polygon
        [-1.82,46.56,5.25,44.04] [whole-world]
        [-1.74,46.98,5.25,44.04] [whole-world wide-north]
        [-1.74 47.05 5.27 44.04] [whole-world wide-north]

        ;; vertical slice of earth
        [-10 90 10 -90] [whole-world on-np on-sp wide-north wide-south polygon-with-holes
                         normal-poly normal-brs north-pole south-pole normal-point
                         very-wide-cart wide-north-cart wide-south-cart normal-poly-cart
                         polygon-with-holes-cart]

        ;; crosses am
        [166.11,53.04,-166.52,-19.14] [whole-world across-am-poly across-am-br am-point
                                       very-wide-cart along-am-line]

        ;; Matches geodetic line
        [17.67,-4,25.56,-6.94] [whole-world normal-line]

        ;; Matches cartesian line
        [23.59,-4,25.56,-15.47] [whole-world normal-line-cart]

        ;; whole world
        [-180 90 180 -90] all-colls))

    (testing "bounding rectangle searches using JSON query"
      (are [value items]
        (let [found (search/find-refs-with-json-query :collection {:page-size 50} {:bounding_box value})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :entry-title) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        [-23.43 -6.31 25.54 5] [whole-world polygon-with-holes normal-poly normal-brs]
        {:west -23.43
         :south -6.31
         :east 25.54
         :north 5} [whole-world polygon-with-holes normal-poly normal-brs]


        ;; inside different holes in cartesian polygon
        [3.57,-18.63,3.84,-14.38] [whole-world normal-poly-cart polygon-with-holes-cart]
        {:west 3.57
         :south -18.63
         :east 3.84
         :north -14.38} [whole-world normal-poly-cart polygon-with-holes-cart]

        ;; vertical slice of earth
        [-10 -90 10 90] [whole-world on-np on-sp wide-north wide-south polygon-with-holes
                         normal-poly normal-brs north-pole south-pole normal-point
                         very-wide-cart wide-north-cart wide-south-cart normal-poly-cart
                         polygon-with-holes-cart]
        {:west -10
         :south -90
         :east 10
         :north 90} [whole-world on-np on-sp wide-north wide-south polygon-with-holes
                     normal-poly normal-brs north-pole south-pole normal-point
                     very-wide-cart wide-north-cart wide-south-cart normal-poly-cart
                     polygon-with-holes-cart]

        ;; crosses am
        [166.11,-19.14,-166.52,53.04] [whole-world across-am-poly across-am-br am-point
                                       very-wide-cart along-am-line]
        {:west 166.11
         :south -19.14
         :east -166.52
         :north 53.04} [whole-world across-am-poly across-am-br am-point
                        very-wide-cart along-am-line]
        ;; Matches geodetic line
        [17.67,-6.94,25.56,-4] [whole-world normal-line]
        {:west 17.67
         :south -6.94
         :east 25.56
         :north -4} [whole-world normal-line]

        ;; whole world
        [-180 -90 180 90] all-colls
        {:west -180
         :south -90
         :east 180
         :north 90} all-colls))

    (testing "polygon searches"
      (are [ords items]
        (let [found (search/find-refs :collection {:polygon (apply search-poly ords)})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :entry-title) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        [20.16,-13.7, 21.64,12.43, 12.47,11.84, -22.57,7.06, 20.16,-13.7]
        [whole-world normal-poly normal-brs polygon-with-holes normal-line normal-line-cart]

        ;; Intersects 2nd of normal-brs
        [-16.79,-12.71,-6.32,-10.95,-5.74,-6.11,-15.18,-7.63,-16.79,-12.71]
        [whole-world normal-poly normal-brs]

        [0.53,39.23,21.57,59.8,-112.21,84.48,-13.37,40.91,0.53,39.23]
        [whole-world on-np wide-north very-wide-cart]

        ;; around north pole
        [58.41,76.95,163.98,80.56,-122.99,81.94,-26.18,82.82,58.41,76.95]
        [whole-world on-np touches-np north-pole very-tall-cart along-am-line]

        ;; around south pole
        [-161.53,-69.93,25.43,-51.08,13.89,-39.94,-2.02,-40.67,-161.53,-69.93]
        [whole-world on-sp wide-south touches-sp south-pole very-tall-cart]

        ;; Across antimeridian
        [-163.9,49.6,171.51,53.82,166.96,-11.32,-168.36,-14.86,-163.9,49.6]
        [whole-world across-am-poly across-am-br am-point very-wide-cart along-am-line]

        [-2.212 -12.44, 0.103 -15.911, 2.185 -11.161 -2.212 -12.44]
        [whole-world normal-poly-cart polygon-with-holes-cart]

        ;; Interactions with lines
        ;; Covers both lines
        [15.42,-15.13, 36.13,-14.29, 25.98,-0.75, 13.19,0.05, 15.42,-15.13]
        [whole-world normal-line normal-line-cart normal-brs]

        ;; Intersects both lines
        [23.33,-14.96,24.02,-14.69,19.73,-6.81,18.55,-6.73,23.33,-14.96]
        [whole-world normal-line normal-line-cart]

        ;; Related to the geodetic polygon with the holes
        ;; Inside holes
        [4.1,0.64,4.95,0.97,6.06,1.76,3.8,1.5,4.1,0.64] [whole-world]
        [1.41,5.12,3.49,5.52,2.66,6.11,0.13,6.23,1.41,5.12] [whole-world]
        ;; Partially inside a hole
        [3.58,-1.34,4.95,0.97,6.06,1.76,3.8,1.5,3.58,-1.34]
        [whole-world polygon-with-holes]
        ;; Covers a hole
        [3.58,-1.34,5.6,0.05,7.6,2.33,2.41,2.92,3.58,-1.34]
        [whole-world polygon-with-holes]
        ;; points inside both holes
        [4.44,0.66,5.4,1.35,2.66,6.11,0.13,6.23,4.44,0.66]
        [whole-world polygon-with-holes]
        ;; completely covers the polygon with holes
        [-6.45,-3.74,12.34,-4.18,12,9.45,-6.69,9.2,-6.45,-3.74]
        [whole-world polygon-with-holes normal-brs]

        ;; Related to the cartesian polygon with the holes
        ;; Inside holes
        [-1.39,-14.32,2.08,-14.38,1.39,-13.43,-1.68,-13.8,-1.39,-14.32]
        [whole-world normal-poly-cart]
        ;; Partially inside a hole
        [-1.39,-14.32,2.08,-14.38,1.64,-12.45,-1.68,-13.8,-1.39,-14.32]
        [whole-world polygon-with-holes-cart normal-poly-cart]
        ;; Covers a hole
        [-3.24,-15.58,5.22,-15.16,6.05,-12.37,-1.98,-12.46,-3.24,-15.58]
        [whole-world polygon-with-holes-cart normal-poly-cart]
        ;; points inside both holes
        [3.98,-18.64,5.08,-18.53,3.7,-13.78,-0.74,-13.84,3.98,-18.64]
        [whole-world polygon-with-holes-cart normal-poly-cart]
        ;; completely covers the polygon with holes
        [-5.95,-23.41,12.75,-23.69,11.11,-10.38,-6.62,-10.89,-5.95,-23.41]
        [whole-world polygon-with-holes-cart wide-south-cart normal-poly-cart]))

    (testing "multiple bounding-box searches should return collections which intersect all the
             supplied bounding boxes"
      (are [wnes-vec items]
        (let [found (search/find-refs :collection {:bounding-box
                                                   (map #(codec/url-encode (apply m/mbr %)) wnes-vec)
                                                   :page-size 50})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :entry-title) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        [[-23.43 5 25.54 -6.31]]
        [whole-world polygon-with-holes normal-poly normal-brs]

        [[-1.74 47.05 5.27 44.04]]
        [whole-world wide-north]

        [[-23.43 5 25.54 -6.31]
         [-1.74 47.05 5.27 44.04]]
        [whole-world]))

    (testing "multiple polygon searches should return collections which intersect all the supplied
             bounding boxes"
      (are [ords-vec items]
        (let [found (search/find-refs :collection {:polygon
                                                   (map (partial apply search-poly) ords-vec)})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :entry-title) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        [[58.41,76.95,163.98,80.56,-122.99,81.94,-26.18,82.82,58.41,76.95]]
        [whole-world on-np touches-np north-pole very-tall-cart along-am-line]

        [[-161.53,-69.93,25.43,-51.08,13.89,-39.94,-2.02,-40.67,-161.53,-69.93]]
        [whole-world on-sp wide-south touches-sp south-pole very-tall-cart]

        [[58.41,76.95,163.98,80.56,-122.99,81.94,-26.18,82.82,58.41,76.95]
         [-161.53,-69.93,25.43,-51.08,13.89,-39.94,-2.02,-40.67,-161.53,-69.93]]
        [whole-world very-tall-cart]))

    (testing "valid circle searches"
      (are [lon-lat-radius items]
        (let [found (search/find-refs
                     :collection
                     {:circle lon-lat-radius
                      :page-size 50})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :entry-title) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        ;; single circle
        "0,0,1000" [whole-world polygon-with-holes]
        ["0,0,1000"] [whole-world polygon-with-holes]

        ;; same center, different radius
        ["0,89,10"] [whole-world on-np]
        ["0,89,100"] [whole-world on-np]
        ["0,89,1000"] [whole-world on-np]
        ["0,89,10000"] [whole-world on-np]
        ["0,89,100000"] [whole-world on-np touches-np]
        ["0,89,1000000"] [whole-world north-pole on-np touches-np very-tall-cart along-am-line]

        ;; cross antimeridian
        ["179.8,41,100000"] [whole-world across-am-poly along-am-line]
        ["-179.9,22,100000"] [whole-world am-point along-am-line]

        ;; multiple circles are ANDed together
        ["0,89,100" "0,89,1000000"] [whole-world on-np]
        ["0,0,1000" "0,89,1000" "0,89,1000000"] [whole-world]))

    (testing "valid circle searches with ORed results"
      (are [lon-lat-radius items]
        (let [found (search/find-refs
                     :collection
                     {:circle lon-lat-radius
                      :page-size 50
                      "options[spatial][or]" "true"})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :entry-title) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        ["0,89,100" "0,89,1000000"] [along-am-line north-pole on-np touches-np very-tall-cart whole-world]
        ["0,0,1000" "0,89,1000" "0,89,1000000"] [along-am-line north-pole on-np polygon-with-holes touches-np very-tall-cart whole-world]
        ["179.8,41,100000" "-179.9,22,100000"] [whole-world am-point across-am-poly along-am-line]))

    (testing "AQL spatial search"
      (are [type ords items]
        (let [refs (search/find-refs-with-aql :collection [{type ords}])
              result (d/refs-match? items refs)]
          (when-not result
            (println "Expected:" (pr-str (map :entry-title items)))
            (println "Actual:" (pr-str (map :name (:refs refs)))))
          result)
        :polygon [20.16,-13.7, 21.64,12.43, 12.47,11.84, -22.57,7.06,20.16,-13.7]
        [whole-world normal-poly normal-brs polygon-with-holes normal-line normal-line-cart]

        :box [23.59,-4,25.56,-15.47] [whole-world normal-line-cart]

        ;; Across antimeridian
        :box [170 20 -170 10]
        [whole-world across-am-br along-am-line]

        :box [166.11,53.04,-166.52,-19.14]
        [whole-world across-am-poly across-am-br am-point very-wide-cart along-am-line]

        :point [17.73 2.21] [whole-world normal-brs]
        :line [-0.37,-14.07,4.75,1.27,25.13,-15.51]
        [whole-world polygon-with-holes polygon-with-holes-cart normal-line-cart normal-line
         normal-poly-cart]))

    (testing "ORed spatial search"
      (let [poly-coordinates ["-16.79,-12.71,-6.32,-10.95,-5.74,-6.11,-15.18,-7.63,-16.79,-12.71"
                              "0.53,39.23,21.57,59.8,-112.21,84.48,-13.37,40.91,0.53,39.23"]
            poly-refs (search/find-refs
                       :collection
                       {:polygon poly-coordinates
                        "options[spatial][or]" "true"})
            bbox-refs (search/find-refs
                       :collection
                       {:bounding-box ["166.11,-19.14,-166.52,53.04"
                                       "23.59,-15.47,25.56,-4"]
                        "options[spatial][or]" "true"})
            combined-refs (search/find-refs
                           :collection
                           {:circle "179.8,41,100000"
                            :bounding-box "166.11,-19.14,-166.52,53.04"
                            "options[spatial][or]" "true"})
            anded-refs (search/find-refs
                        :collection
                        {:circle "179.8,41,100000"
                         :bounding-box "166.11,-19.14,-166.52,53.04"
                         "options[spatial][or]" "false"})
            bbox-with-other-options (search/find-refs
                                     :collection
                                     {:bounding-box ["166.11,-19.14,-166.52,53.04"
                                                     "23.59,-15.47,25.56,-4"]
                                      :include_granule_counts "true"
                                      "options[spatial][or]" "true"})]
        (is (d/refs-match? [across-am-poly along-am-line whole-world across-am-br am-point very-wide-cart]
                           combined-refs))
        (is (d/refs-match? [wide-north on-np normal-poly very-wide-cart whole-world normal-brs]
                           poly-refs))
        (is (d/refs-match? [across-am-poly very-wide-cart am-point along-am-line normal-line-cart whole-world across-am-br]
                           bbox-refs))
        (is (d/refs-match? [across-am-poly along-am-line whole-world]
                           anded-refs))
        (is (d/refs-match? [across-am-poly very-wide-cart am-point along-am-line normal-line-cart whole-world across-am-br]
                           bbox-with-other-options))))

    (testing "ORed spatial search with other search params"
      (is (d/refs-match? [across-am-br]
                         (search/find-refs
                          :collection
                          {:circle "179.8,41,100000"
                           :bounding-box "166.11,-19.14,-166.52,53.04"
                           :entry-title "across-am-br"
                           "options[spatial][or]" "true"}))))))

(def all-tiles
  [[8 8] [35 7] [7 6] [28 8] [27 8] [8 7] [16 6] [8 11] [22 10] [9 8] [10 14] [12 12] [8 9]
   [7 12] [34 11] [26 13][27 9] [12 6] [15 4] [13 3] [28 5] [23 5] [10 5] [13 15] [15 11] [11 9]
   [11 2] [7 11] [17 5] [21 10] [19 6] [7 13] [22 7] [18 12] [19 16] [21 11] [21 7] [25 10] [3 9]
   [13 8] [35 10] [4 12] [7 7] [18 3] [2 8] [23 2] [31 10] [27 3] [10 15] [13 6] [20 14] [22 5]
   [14 17] [22 12] [17 2] [27 14] [19 9] [8 4] [17 6] [18 7] [2 5] [16 5] [30 10] [15 16] [10 13]
   [15 17] [25 14] [18 0] [6 7] [15 0] [12 13] [33 5] [15 12] [32 8] [12 14] [24 5] [26 10] [7 4]
   [25 7] [8 3] [21 12] [35 8] [9 15] [33 11] [27 5] [31 4] [13 12] [28 10] [10 9] [5 4] [15 3]
   [10 8] [5 10] [18 2] [25 15] [6 3] [32 10] [20 12] [28 3] [27 12] [24 9] [21 3] [17 16] [14 6]
   [27 4] [22 9] [32 5] [11 14] [35 9] [28 14] [17 0] [12 8] [31 11] [12 5] [34 8] [29 8] [7 3]
   [19 12] [8 6] [22 15] [12 2] [26 11] [16 7] [17 12] [31 5] [26 8] [15 9] [17 1] [20 5] [14 13]
   [7 8] [24 6] [3 12] [23 1] [20 16] [26 7] [13 2] [18 10] [22 13] [21 8] [24 13] [26 9] [6 6]
   [9 6] [12 1] [11 13] [13 9] [19 10] [6 13] [16 2] [26 2] [1 9] [18 11] [20 3] [28 6] [8 10]
   [18 4] [18 14] [9 9] [32 7] [24 2] [13 7] [9 3] [30 6] [24 11] [28 4] [29 7] [9 12] [29 10]
   [23 15] [23 12] [4 7] [24 3] [13 1] [20 11] [4 10] [24 12] [19 7] [21 2] [4 9] [25 5] [31 6]
   [32 12] [22 1] [1 10] [2 9] [18 9] [6 5] [11 11] [24 4] [5 13] [29 12] [16 13] [31 7] [33 10]
   [19 1] [4 11] [18 6] [23 16] [0 9] [34 9] [21 6] [20 13] [16 3] [4 6] [14 15] [15 7] [21 1]
   [17 4] [23 11] [16 8] [23 14] [33 9] [27 6] [11 4] [30 11] [10 2] [23 4] [11 8] [1 11] [5 7]
   [22 16] [11 12] [21 4] [16 16] [21 14] [12 7] [27 13] [29 4] [16 0] [27 10] [10 7] [11 10]
   [25 3] [30 4] [4 8] [10 11] [18 1] [12 11] [15 1] [9 14] [13 13] [12 10] [11 6] [14 4] [11 3]
   [15 10] [1 8] [21 17] [1 7] [12 4] [16 17] [6 4] [15 5] [18 8] [22 3] [18 17] [25 11] [30 5]
   [20 8] [21 0] [20 17] [2 12][16 11] [21 13] [19 4] [14 8] [30 7] [18 16] [29 9] [2 11] [6 14]
   [5 11] [5 6] [5 8] [24 15] [13 11] [30 12] [25 4] [8 13] [34 10] [28 7] [8 5] [0 7] [14 11]
   [15 8] [20 10] [6 8] [13 5] [9 11] [6 11] [5 5] [7 9] [10 12] [14 1] [2 7] [11 1] [23 6]
   [26 15] [26 14] [15 6] [5 9] [24 14] [3 6] [19 14] [20 1] [23 9] [32 6] [12 15] [14 5] [7 10]
   [19 13] [20 0] [17 3] [10 6] [23 13] [17 14] [34 7] [22 8] [22 11] [17 10] [9 2] [26 4] [20 7]
   [19 5] [4 5] [34 6] [22 14] [11 7] [9 7] [10 4] [17 15] [31 9] [15 13] [10 10] [24 16] [30 9]
   [33 8] [12 9] [25 12] [33 12] [24 8] [20 9] [19 11] [24 7] [6 9] [24 10] [19 3] [17 11]
   [11 15] [21 5] [23 8] [29 14] [23 7] [16 12] [23 10] [30 13] [29 3] [3 11] [0 10] [25 8]
   [19 0] [20 15] [11 5] [9 13] [26 3] [19 15] [3 10] [12 3] [33 7] [25 6] [16 14] [13 16]
   [17 13] [27 11][14 14] [19 8] [9 5] [6 10] [3 8] [33 6] [9 4] [18 15] [7 14] [14 2] [25 13]
   [26 6] [6 12] [16 1] [14 16] [16 15] [21 15] [4 13] [1 6] [29 5] [16 9] [24 1] [14 3] [4 4]
   [28 12] [27 7] [3 7] [2 10] [7 5] [28 9] [25 9] [29 11] [13 10] [2 6] [16 10] [14 12] [17 9]
   [30 8] [9 10] [8 14] [8 12] [13 4] [32 11] [25 2] [22 6] [16 4] [15 15] [12 16] [21 9] [14 7]
   [22 2] [31 8] [20 2] [14 10] [15 14] [23 3] [19 17] [10 3] [28 13] [18 13] [22 4] [21 16]
   [3 5] [13 14] [0 8] [5 12] [31 13] [17 8] [32 9] [19 2] [20 4] [26 12] [14 9] [18 5] [29 13]
   [11 16] [17 17] [14 0] [29 6] [26 5][15 2] [20 6] [31 12] [28 11] [17 7]])

(defn assert-tiles-found
  "Check if the tiles returned by passing params map as the search parameter matches
  with the expected tiles"
  [params expected]
  (let [found (search/find-tiles params)]
    (is (= (set expected) (set (:results found))))))


(deftest tile-search-single-shape-test
  (testing "bounding box search"
    (u/are2 [wnes tiles]
            (assert-tiles-found {:bounding-box (codec/url-encode (apply m/mbr wnes))} tiles)

            "whole world"
            [-180 90 180 -90] all-tiles

            "Madagascar"
            [42.35 -11.75 51.5 -26.04] [[21 10][21 11] [22 10] [22 11] [23 10]]))

  (testing "polygon search"
    (u/are2 [ords tiles]
            (assert-tiles-found {:polygon (apply search-poly ords)} tiles)

            "A large polygon"
            [7 35  -3.5 22.5  -7 12  -5 1.5  14.5 -9.0  38 5  37 22 7 35]
            [[17 6] [17 7] [17 8] [17 9] [18 5] [18 6] [18 7] [18 8] [18 9] [19 5] [19 6] [19 7]
             [19 8] [19 9] [20 6] [20 7] [20 8] [20 9] [21 6] [21 7] [21 8]]))

  (testing "line search"
    (u/are2 [ords tiles]
            (assert-tiles-found
              {:line (codec/url-encode (l/ords->line-string :geodetic ords))} tiles)

            "A simple line"
            [-62.0  -27.0  -76.5  5.0] [[10 8][10 9][11 9][11 10][11 11][12 11]]

            "A line which crosses over anti-meridian"
            [168  22.5  -158  -4.5] [[0 7] [0 8] [1 8] [1 9] [2 9] [33 6] [34 6] [34 7] [35 7]]))

  (testing "point search"
    (u/are2 [ords tiles]
            (assert-tiles-found {:point (codec/url-encode (apply p/point ords))} tiles)

            "A point"
            [-83.0  40.01] [[11 4]])))

(defn- ords->url-encoded-str
  "Returns a URL encoded string of the given spatial type with the given ordinates"
  [shape-type ords]
  (case shape-type
    :point (codec/url-encode (apply p/point ords))
    :line (codec/url-encode (l/ords->line-string :geodetic ords))
    :polygon (apply search-poly ords)
    :bounding-box (codec/url-encode (apply m/mbr ords))))

(defn- add-param
  "Adds a spatial parameter with the given spatial type and ords into params map"
  [params [spatial-type ords]]
  (assoc params spatial-type (conj (spatial-type params)
                                   (ords->url-encoded-str spatial-type ords))))

(deftest tile-search-multi-shape-test
  (testing "search involving multiple shapes"
    (u/are2 [ords-vectors tiles]
            (assert-tiles-found (reduce add-param {} ords-vectors) tiles)

            "Empty parameters"
            [] all-tiles

            "Two bounding boxes"
            [[:bounding-box [-5 5 5 -5]]
             [:bounding-box [0 20 20 0]]]
            [[19 6] [19 9] [17 6] [18 7] [19 7] [18 9] [18 6]
             [18 8][20 8] [20 9] [19 8] [17 9] [17 8] [17 7]]

            "Two bounding boxes, a point & a line"
            [[:bounding-box [-180 90 180 -90]]
             [:bounding-box [-20 20 20 -20]]
             [:point [0 0]]
             [:line [0 10 20 20]]]
            all-tiles)))

(deftest multi-valued-spatial-parameter-validation
  (testing "multi valued spatial parameter validation with invalid parameters"
    (let [{:keys [status errors]} (search/find-refs :collection {"line[]" ["20b,30,80,60","10a,10,20,20"]})]
      (is (= 400 status))
      (is (= ["[20b,30,80,60] is not a valid URL encoded line"
              "[10a,10,20,20] is not a valid URL encoded line"] errors)))))

(deftest circle-parameter-validation
  (testing "invalid circle parameters"
    (are3 [params error-msgs]
      (let [{:keys [status errors]} (search/find-refs :collection {:circle params})]
        (is (= [400 error-msgs]
               [status errors])))

      "circle invalid format -- not enough values"
      ["0,100"]
      ["[0,100] is not a valid URL encoded circle"]

      "circle invalid format -- not enough values (not list)"
      "0,100"
      ["[0,100] is not a valid URL encoded circle"]

      "circle invalid format -- too many values (not list)"
      "0,0,100,"
      ["[0,0,100,] is not a valid URL encoded circle"]

      "circle invalid format -- too many values"
      ["0,0,100,200"]
      ["[0,0,100,200] is not a valid URL encoded circle"]

      "circle center longitude wrong format"
      ["x,0,100"]
      ["[x,0,100] is not a valid URL encoded circle"]

      "circle center latitude wrong format"
      ["0,y,100"]
      ["[0,y,100] is not a valid URL encoded circle"]

      "circle radius wrong format"
      ["0,1,r"]
      ["[0,1,r] is not a valid URL encoded circle"]

      "circle center longitude out of range"
      ["181,0,100"]
      ["Point longitude [181] must be within -180.0 and 180.0"]

      "circle center latitude out of range"
      ["0,-91,100"]
      ["Point latitude [-91] must be within -90 and 90.0"]

      "center is on north pole"
      ["0,90,100"]
      ["Circle center cannot be the north or south pole, but was [0.0, 90.0]"]

      "center is on south pole"
      ["120,-90,100"]
      ["Circle center cannot be the north or south pole, but was [120.0, -90.0]"]

      "circle radius too small"
      ["0,0,1.0"]
      ["Circle radius must be between 10 and 6000000, but was 1.0."]

      "circle radius too large"
      ["0,0,6000001"]
      ["Circle radius must be between 10 and 6000000, but was 6000001.0."]

      "multiple circles errors"
      ["0,1,r" "0,0,100" "10,20,-100"]
      ["[0,1,r] is not a valid URL encoded circle"
       "Circle radius must be between 10 and 6000000, but was -100.0."])))
