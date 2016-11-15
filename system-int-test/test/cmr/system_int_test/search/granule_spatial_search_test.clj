(ns cmr.system-int-test.search.granule-spatial-search-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common.dev.util :as dev-util]
   [cmr.spatial.arc :as a]
   [cmr.spatial.cartesian-ring :as cr]
   [cmr.spatial.codec :as codec]
   [cmr.spatial.derived :as derived]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-segment :as s]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.lr-binary-search :as lbs]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.messages :as smsg]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.serialize :as srl]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.umm-spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(comment

  (dev-sys-util/reset)
  (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"}))




(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order.
  The polygon will be closed automatically."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn search-poly
  "Returns a url encoded polygon for searching"
  [& ords]
  (codec/url-encode (umm-s/set-coordinate-system :geodetic (apply polygon ords))))

;; Tests search failure conditions
(deftest spatial-search-validation-test
  (testing "All granules spatial"
    (testing "Success"
      (are3 [params]
        (let [response (search/find-refs :granule params)]
          (is (= 0 (:hits response))
              (pr-str response)))
        "All granules no spatial is allowed"
        {}
        "Point with provider"
        {:point "0,0" :provider "PROV1"}
        "Box with provider"
        {:bounding-box "-10,-5,10,5" :provider "PROV1"}
        "Polygon with provider"
        {:polygon "10,10,30,10,30,20,10,20,10,10" :provider "PROV1"}
        "Line with provider"
        {:line "10,10,30,10" :provider "PROV1"}

        "Multiple providers"
        {:point "0,0" :provider ["PROV1" "P2"]}
        "Echo granule id"
        {:point "0,0" :echo-granule-id "G4-P1"}
        "Echo collection id"
        {:point "0,0" :echo-collection-id "C4-P1"}
        "Concept id"
        {:point "0,0" :concept-id "G4-P1"}
        "Collection concept id"
        {:point "0,0" :concept-id "C4-P1"}
        "Short name"
        {:point "0,0" :short-name "C4-P1"}
        "Entry Title"
        {:point "0,0" :entry-title "foo"}
        "Version"
        {:point "0,0" :version "foo"}))
    (testing "Rejected"
      (are3 [params]
        (is (re-find #"The CMR does not allow querying across granules in all collections with a spatial condition"
                     (first (:errors (search/find-refs :granule params)))))
        "Point"
        {:point "0,0"}
        "Box"
        {:bounding-box "-10,-5,10,5"}
        "Polygon"
        {:polygon "10,10,30,10,30,20,10,20,10,10"}
        "Line"
        {:line "10,10,30,10"})))

  (testing "invalid encoding"
    (is (= {:errors [(smsg/shape-decode-msg :polygon "0,ad,d,0")] :status 400}
           (search/find-refs :granule {:polygon "0,ad,d,0" :provider "PROV1"})))
    (is (= {:errors [(smsg/shape-decode-msg :bounding-box "0,ad,d,0")] :status 400}
           (search/find-refs :granule {:bounding-box "0,ad,d,0" :provider "PROV1"})))
    (is (= {:errors [(smsg/shape-decode-msg :point "0,ad")] :status 400}
           (search/find-refs :granule {:point "0,ad" :provider "PROV1"}))))

  (testing "invalid polygons"
    (is (= {:errors [(smsg/ring-not-closed)] :status 400}
           (search/find-refs :granule
                             {:provider "PROV1"
                              :polygon (codec/url-encode
                                        (poly/polygon :geodetic [(rr/ords->ring :geodetic [0 0, 1 0, 1 1, 0 1])]))}))))
  (testing "invalid bounding box"
    (is (= {:errors [(smsg/br-north-less-than-south 45 46)] :status 400}
           (search/find-refs
            :granule
            {:bounding-box (codec/url-encode (m/mbr -180 45 180 46)) :provider "PROV1"}))))

  (testing "invalid point"
    (is (= {:errors [(smsg/point-lon-invalid -181)] :status 400}
           (search/find-refs :granule {:point "-181.0,5" :provider "PROV1"}))))

  (testing "invalid lines"
    (is (= {:errors [(smsg/duplicate-points [[1 (p/point 1 1)] [3 (p/point 1 1)]])] :status 400}
           (search/find-refs :granule
                             {:line "0,0,1,1,2,2,1,1" :provider "PROV1"})))))


(deftest spatial-search-test
  (let [geodetic-coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial {:gsr :geodetic})}))
        cartesian-coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial {:gsr :cartesian})}))
        make-gran (fn [ur & shapes]
                    (let [shapes (map (partial umm-s/set-coordinate-system :geodetic) shapes)]
                      (d/ingest "PROV1" (dg/granule geodetic-coll
                                                    {:granule-ur ur
                                                     :spatial-coverage (apply dg/spatial shapes)}))))
        make-cart-gran (fn [ur & shapes]
                         (let [shapes (map (partial umm-s/set-coordinate-system :cartesian) shapes)]
                           (d/ingest "PROV1" (dg/granule cartesian-coll
                                                         {:granule-ur ur
                                                          :spatial-coverage (apply dg/spatial shapes)}))))

        ;; Lines
        normal-line (make-gran "normal-line" (l/ords->line-string :geodetic [22.681 -8.839, 18.309 -11.426, 22.705 -6.557]))
        normal-line-cart (make-cart-gran "normal-line-cart" (l/ords->line-string :cartesian [16.439 -13.463,  31.904 -13.607, 31.958 -10.401]))

        ;; Bounding rectangles
        whole-world (make-gran "whole-world" (m/mbr -180 90 180 -90))
        touches-np (make-gran "touches-np" (m/mbr 45 90 55 70))
        touches-sp (make-gran "touches-sp" (m/mbr -160 -70 -150 -90))
        across-am-br (make-gran "across-am-br" (m/mbr 170 10 -170 -10))
        normal-brs (make-gran "normal-brs"
                              (m/mbr 10 10 20 0)
                              (m/mbr -20 0 -10 -10))

        ;; Geodetic Polygons
        wide-north (make-gran "wide-north" (polygon -70 20, 70 20, 70 30, -70 30, -70 20))
        wide-south (make-gran "wide-south" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        across-am-poly (make-gran "across-am-poly" (polygon 170 35, -175 35, -170 45, 175 45, 170 35))
        on-np (make-gran "on-np" (polygon 45 85, 135 85, -135 85, -45 85, 45 85))
        on-sp (make-gran "on-sp" (polygon -45 -85, -135 -85, 135 -85, 45 -85, -45 -85))
        normal-poly (make-gran "normal-poly" (polygon -20 -10, -10 -10, -10 10, -20 10, -20 -10))

        ;; polygon with holes
        outer (umm-s/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (umm-s/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (umm-s/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (make-gran "polygon-with-holes" (poly/polygon [outer hole1 hole2]))

        ;; Cartesian Polygons
        wide-north-cart (make-cart-gran "wide-north-cart" (polygon -70 20, 70 20, 70 30, -70 30, -70 20))
        wide-south-cart (make-cart-gran "wide-south-cart" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        very-wide-cart (make-cart-gran "very-wide-cart" (polygon -180 40, -180 35, 180 35, 180 40, -180 40))
        very-tall-cart (make-cart-gran "very-tall-cart" (polygon -160 90, -160 -90, -150 -90, -150 90, -160 90))
        normal-poly-cart (make-cart-gran "normal-poly-cart" (polygon 1.534 -16.52, 6.735 -14.102, 3.745 -9.735, -1.454 -11.802, 1.534 -16.52))

        outer-cart (umm-s/ords->ring -5.26 -22.59 11.56 -22.77 10.47 -11.29 -5.86 -11.37 -5.26 -22.59)
        hole1-cart (umm-s/ords->ring 6.95 -17.95 2.98 -17.94 3.92 -20.08 6.95 -17.95)
        hole2-cart (umm-s/ords->ring 5.18 -13.08 -1.79 -12.99 -2.65 -15 4.29 -14.95 5.18 -13.08)
        polygon-with-holes-cart (make-cart-gran "polygon-with-holes-cart" (poly/polygon [outer-cart hole1-cart hole2-cart]))

        ;; Points
        north-pole (make-gran "north-pole" (p/point 0 90))
        south-pole (make-gran "south-pole" (p/point 0 -90))
        normal-point (make-gran "normal-point" (p/point 10 22))
        am-point (make-gran "am-point" (p/point 180 22))
        all-grans [whole-world touches-np touches-sp across-am-br normal-brs
                   wide-north wide-south across-am-poly on-sp on-np normal-poly
                   polygon-with-holes north-pole south-pole normal-point am-point
                   very-wide-cart very-tall-cart wide-north-cart wide-south-cart
                   normal-poly-cart polygon-with-holes-cart normal-line normal-line-cart]]
    (index/wait-until-indexed)

    (testing "line searches"
      (are [ords items]
        (let [found (search/find-refs
                     :granule
                     {:line (codec/url-encode (l/ords->line-string :geodetic ords))
                      :provider "PROV1"
                      :page-size 50})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :granule-ur) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        ;; normal two points
        [-24.28,-12.76,10,10] [whole-world polygon-with-holes normal-poly normal-brs]

        ;; normal multiple points
        [-0.37,-14.07,4.75,1.27,25.13,-15.51] [whole-world polygon-with-holes
                                               polygon-with-holes-cart normal-line-cart
                                               normal-line normal-poly-cart]
        ;; across antimeridian
        [-167.85,-9.08,171.69,43.24] [whole-world across-am-br across-am-poly very-wide-cart]

        ;; across north pole
        [0 85, 180 85] [whole-world north-pole on-np touches-np]

        ;; across north pole where cartesian polygon touches it
        [-155 85, 25 85] [whole-world north-pole on-np very-tall-cart]

        ;; across south pole
        [0 -85, 180 -85] [whole-world south-pole on-sp]

        ;; across north pole where cartesian polygon touches it
        [-155 -85, 25 -85] [whole-world south-pole on-sp touches-sp very-tall-cart]))

    (testing "point searches"
      (are [lon_lat items]
        (let [found (search/find-refs :granule {:point (codec/url-encode (apply p/point lon_lat))
                                                :provider "PROV1"
                                                :page-size 50})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :granule-ur) sort pr-str))
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
        (let [found (search/find-refs :granule {:bounding-box (codec/url-encode (apply m/mbr wnes))
                                                :provider "PROV1"
                                                :page-size 50})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :granule-ur) sort pr-str))
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
        [166.11,53.04,-166.52,-19.14] [whole-world across-am-poly across-am-br am-point very-wide-cart]

        ;; Matches geodetic line
        [17.67,-4,25.56,-6.94] [whole-world normal-line]

        ;; Matches cartesian line
        [23.59,-4,25.56,-15.47] [whole-world normal-line-cart]

        ;; whole world
        [-180 90 180 -90] all-grans))

    (testing "polygon searches"
      (are [ords items]
        (let [found (search/find-refs :granule {:polygon (apply search-poly ords)
                                                :provider "PROV1"})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :granule-ur) sort pr-str))
            (println "Actual:" (->> found :refs (map :name) sort pr-str)))
          matches?)

        [20.16,-13.7,21.64,12.43,12.47,11.84,-22.57,7.06,20.16,-13.7]
        [whole-world normal-poly normal-brs polygon-with-holes normal-line normal-line-cart]

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
        [whole-world normal-poly-cart polygon-with-holes-cart]

        ;; Interactions with lines
        ;; Covers both lines
        [15.42,-15.13,36.13,-14.29,25.98,-0.75,13.19,0.05,15.42,-15.13]
        [whole-world normal-line normal-line-cart normal-brs]

        ;; Intersects both lines
        [23.33,-14.96,24.02,-14.69,19.73,-6.81,18.55,-6.73,23.33,-14.96]
        [whole-world normal-line normal-line-cart]

        ;; Related to the geodetic polygon with the holes
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
        [-6.45,-3.74,12.34,-4.18,12,9.45,-6.69,9.2,-6.45,-3.74] [whole-world polygon-with-holes normal-brs]

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

    (testing "AQL spatial search"
      (are [type ords items]
        (let [refs (search/find-refs-with-aql :granule [{type ords}] {:dataCenterId "PROV1"})
              result (d/refs-match? items refs)]
          (when-not result
            (println "Expected:" (pr-str (map :entry-title items)))
            (println "Actual:" (pr-str (map :name (:refs refs)))))
          result)
        :polygon [20.16,-13.7,21.64,12.43,12.47,11.84,-22.57,7.06,20.16,-13.7]
        [whole-world normal-poly normal-brs polygon-with-holes normal-line normal-line-cart]

        :box [23.59,-4,25.56,-15.47] [whole-world normal-line-cart]
        :point [17.73 2.21] [whole-world normal-brs]
        :line [-0.37,-14.07,4.75,1.27,25.13,-15.51]
        [whole-world polygon-with-holes polygon-with-holes-cart normal-line-cart normal-line
         normal-poly-cart]))))

(deftest no-lr-spatial-search-test
  (let [geodetic-coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial {:gsr :geodetic})}))
        make-gran (fn [ur & shapes]
                    (let [shapes (map (partial umm-s/set-coordinate-system :geodetic) shapes)]
                      (d/ingest "PROV1" (dg/granule geodetic-coll
                                                    {:granule-ur ur
                                                     :spatial-coverage (apply dg/spatial shapes)}))))
        no-lr (make-gran "no-lr" (polygon 0.0 0.0, -179.9998 -89.9999, 0.0 -89.9999, 0.0 0.0))]

    ;; This particular polygon is problematic. We can't find an interial rectangle for it. So we
    ;; are using one of the points in the polyson to create a mbr to replace it. 
    (index/wait-until-indexed)
    (are3 [ords items]
      (let [found (search/find-refs :granule {:polygon (apply search-poly ords)
                                              :provider "PROV1"})]
        (d/assert-refs-match items found))
 
      "search against the original polygon matching case" 
      [0.0 0.0, -179.9998 -89.9999, 0.0 -89.9999, 0.0 0.0]
      [no-lr]

      "search against the box that covers one of the points (0.0 0.0) matching case"
      [-10 -10, 10 -10, 10 10, -10 10, -10 -10]
      [no-lr]

      "search against the box that covers all three points matching case"
      [-179.9998 0, -179.9998 -89.9999, 0 -89.9999, 0 0, -179.9998 0]
      [no-lr])

     (are3 [ords items]
       (let [response (search/find-refs :granule {:polygon (apply search-poly ords)
                                                  :provider "PROV1"})]
         (is (= 0 (:hits response))
             (pr-str response))) 

       "search against the box that does not intersect with the polygon unmatching case"
       [1 1, 2 1, 2 2, 1 2, 1 1]
       [no-lr]

       "search against the box that does not intersect with the polygon but intersect with the mbr of the polygon unmatching case"
       [-179 0, -179 -1, -178 -1, -178 0, -179 0]
       [no-lr]

       "search against a polygon whoes number of points does not exceed the max number of points (400) unmaching case"
      [45.548821,52.267059,45.549019,52.265549,45.549126,52.265541,45.549698,52.265549,45.550209,52.265595,45.550774,52.26564,45.551346,52.265671,45.551933,52.265709,45.552536,52.265762,45.553124,52.265801,45.553711,52.265846,45.554306,52.265884,45.554901,52.26593,45.555496,52.265968,45.556374,52.266037,45.556969,52.266083,45.557556,52.266136,45.558136,52.266174,45.558701,52.266213,45.559563,52.266274,45.560135,52.266312,45.560715,52.26635,45.561302,52.26638,45.561874,52.266419,45.562752,52.266464,45.563332,52.266502,45.563896,52.266541,45.564423,52.266571,45.564926,52.266602,45.565872,52.266663,45.566399,52.266693,45.566895,52.266731,45.567032,52.266739,45.567085,52.266792,45.567055,52.266914,45.567047,52.267166,45.567062,52.267265,45.56707,52.26751,45.567078,52.267578,45.567085,52.267639,45.567078,52.267731,45.567085,52.267815,45.567116,52.268044,45.567123,52.268456,45.567131,52.268814,45.567116,52.26918,45.567108,52.269501,45.567123,52.269928,45.567139,52.27018,45.567146,52.270462,45.567146,52.270874,45.567139,52.271156,45.567139,52.2715,45.567146,52.27179,45.567162,52.272087,45.567177,52.272461,45.567162,52.272774,45.567146,52.273087,45.567146,52.273415,45.567223,52.273544,45.567268,52.273613,45.567299,52.273666,45.567291,52.273766,45.567291,52.273872,45.567276,52.274185,45.567268,52.274292,45.567268,52.274345,45.567238,52.274445,45.567238,52.274635,45.567253,52.274719,45.567253,52.274857,45.567238,52.275047,45.56723,52.275139,45.567238,52.275208,45.567238,52.275284,45.567238,52.275391,45.567246,52.275528,45.567238,52.275726,45.567246,52.275925,45.567238,52.276047,45.567215,52.276138,45.567215,52.276222,45.567207,52.276268,45.567253,52.276474,45.567284,52.276581,45.567352,52.276772,45.567368,52.276825,45.567352,52.276985,45.567329,52.2771,45.567329,52.277161,45.567352,52.277268,45.567413,52.277435,45.567429,52.277489,45.567406,52.277542,45.567337,52.277794,45.567314,52.277908,45.567291,52.278099,45.567307,52.278397,45.567307,52.278679,45.567284,52.278832,45.567177,52.279137,45.567154,52.279327,45.567154,52.279694,45.567162,52.280037,45.567177,52.280335,45.567184,52.280632,45.5672,52.280945,45.567207,52.280998,45.5672,52.281044,45.567192,52.281105,45.5672,52.281212,45.5672,52.281319,45.567184,52.281395,45.567184,52.28157,45.567192,52.281899,45.567192,52.282234,45.567192,52.282539,45.5672,52.282867,45.5672,52.28315,45.5672,52.28344,45.567215,52.283752,45.567215,52.284081,45.567215,52.284393,45.567276,52.284531,45.567307,52.284584,45.567307,52.284668,45.567268,52.284798,45.567261,52.284996,45.567276,52.285111,45.567299,52.285172,45.567307,52.285423,45.567268,52.285561,45.567253,52.285606,45.567238,52.285721,45.567238,52.286064,45.567238,52.286346,45.567238,52.286697,45.567238,52.287041,45.567253,52.287369,45.567253,52.287674,45.567246,52.287964,45.567246,52.288277,45.567246,52.28862,45.567253,52.288933,45.567261,52.289261,45.567268,52.289574,45.567284,52.289696,45.567246,52.289764,45.567238,52.289871,45.567253,52.290222,45.567261,52.29055,45.567268,52.290878,45.567284,52.291176,45.567291,52.291473,45.567307,52.291748,45.567322,52.292114,45.567337,52.292442,45.567352,52.292572,45.56736,52.292839,45.567352,52.292915,45.567284,52.292992,45.567131,52.293045,45.567032,52.293068,45.566765,52.293114,45.566261,52.29319,45.565765,52.293266,45.565277,52.293343,45.564804,52.293427,45.564323,52.293503,45.563873,52.293572,45.563286,52.293633,45.563004,52.293678,45.562874,52.293694,45.562744,52.293709,45.562614,52.293709,45.5625,52.293732,45.562386,52.293755,45.561897,52.293831,45.561806,52.293846,45.561562,52.293877,45.561363,52.293892,45.561165,52.293907,45.56102,52.293922,45.560883,52.293938,45.560738,52.293922,45.560654,52.293907,45.56057,52.293861,45.560486,52.293785,45.560432,52.293694,45.560387,52.293465,45.560379,52.293411,45.560364,52.293114,45.560356,52.29306,45.560364,52.293015,45.560371,52.292961,45.560402,52.292915,45.560509,52.292793,45.560562,52.292748,45.5607,52.292664,45.560967,52.292526,45.561073,52.292488,45.561348,52.292412,45.561432,52.292397,45.5616,52.292366,45.561844,52.292358,45.562149,52.292366,45.56263,52.292358,45.56295,52.292366,45.563362,52.292397,45.563438,52.292381,45.563461,52.292336,45.563454,52.29229,45.563454,52.292229,45.563438,52.292175,45.563355,52.29213,45.562851,52.292069,45.562355,52.292023,45.562012,52.291962,45.561905,52.291939,45.561684,52.291916,45.561127,52.291908,45.5606,52.291885,45.560486,52.291878,45.560394,52.29187,45.56031,52.29184,45.560234,52.291809,45.560173,52.291771,45.56012,52.291733,45.560043,52.291451,45.559982,52.291153,45.559967,52.291077,45.559929,52.291,45.559891,52.290932,45.559769,52.290817,45.559715,52.290772,45.559639,52.290741,45.559555,52.290733,45.559418,52.290726,45.559204,52.290718,45.559097,52.290718,45.558846,52.290726,45.558746,52.290726,45.558662,52.290718,45.558563,52.29071,45.558472,52.290703,45.55835,52.290703,45.558197,52.290718,45.558037,52.290733,45.557861,52.290741,45.557404,52.290779,45.556877,52.290825,45.556595,52.29084,45.556221,52.290886,45.556076,52.290894,45.55587,52.290886,45.555748,52.290878,45.555649,52.290855,45.555557,52.290817,45.555496,52.290764,45.555473,52.290695,45.555443,52.290619,45.555435,52.290573,45.55542,52.290527,45.555458,52.290367,45.555519,52.290047,45.555565,52.289711,45.555565,52.289597,45.555565,52.289536,45.555573,52.289352,45.55555,52.289116,45.555542,52.288948,45.555542,52.28878,45.555542,52.288719,45.555519,52.288384,45.555519,52.288284,45.55542,52.28801,45.555382,52.287888,45.555367,52.287819,45.555313,52.287613,45.555229,52.287422,45.555214,52.287369,45.555183,52.287315,45.555153,52.287262,45.555107,52.287132,45.554955,52.28682,45.55481,52.286552,45.554764,52.286476,45.554642,52.286285,45.554497,52.286003,45.554329,52.285706,45.554184,52.285431,45.55407,52.285149,45.554047,52.285095,45.554039,52.285049,45.554016,52.284996,45.553909,52.284721,45.553818,52.284401,45.553734,52.284126,45.553711,52.28405,45.55368,52.283989,45.553383,52.283951,45.553337,52.283997,45.553291,52.284042,45.553268,52.284119,45.553261,52.28421,45.553291,52.284493,45.553276,52.284607,45.553284,52.28476,45.553307,52.284874,45.553398,52.285202,45.553429,52.285339,45.55349,52.285538,45.55352,52.285652,45.553581,52.285843,45.553635,52.286041,45.553734,52.286377,45.553825,52.286659,45.553848,52.286728,45.553894,52.286949,45.554024,52.28727,45.554176,52.287598,45.554199,52.287659,45.554215,52.28772,45.554276,52.28788,45.554421,52.288162,45.554489,52.288284,45.55452,52.28833,45.554573,52.288467,45.554634,52.288788,45.554657,52.289116,45.554642,52.289215,45.554573,52.289528,45.554482,52.289818,45.554466,52.289864,45.554436,52.289909,45.55439,52.289948,45.554344,52.289986,45.554283,52.290016,45.554215,52.290047,45.554123,52.290062,45.554024,52.290077,45.553703,52.290108,45.553375,52.290108,45.553124,52.290108,45.552734,52.290131,45.552513,52.290161,45.552185,52.290222,45.552094,52.290237,45.55201,52.290245,45.551743,52.290337,45.551682,52.290367,45.551445,52.290535,45.551361,52.290596,45.55114,52.290749,45.550789,52.290962,45.550728,52.291,45.550674,52.291039,45.550644,52.291084,45.550613,52.291222,45.550606,52.291351,45.550606,52.29142,45.550598,52.291565,45.550575,52.291611,45.550499,52.291702,45.5504,52.291756,45.550331,52.291779,45.549988,52.29184,45.549683,52.291878,45.549164,52.291946,45.548782,52.291992,45.548279,52.292069,45.547791,52.292137,45.547524,52.292175,45.547035,52.292244,45.546944,52.292259,45.546837,52.292275,45.546761,52.292282,45.546677,52.292282,45.5466,52.292282,45.546524,52.292282,45.546288,52.292107,45.546242,52.292069,45.546249,52.291908,45.546265,52.291611,45.546295,52.291275,45.54631,52.29113,45.546326,52.290848,45.546333,52.29055,45.546333,52.290505,45.546349,52.290344,45.546372,52.290024,45.546387,52.289719,45.546455,52.289391,45.546509,52.289032,45.5466,52.288887,45.546639,52.288818,45.546661,52.28859,45.546677,52.288277,45.546661,52.288155,45.548821,52.267059] 
      [no-lr])

      (are3 [ords items]
        (let [response (search/find-refs :granule {:polygon (apply search-poly ords)
                                                   :provider "PROV1"})]
          (is (= 400 (:status response))
              (pr-str response)))

         "search against a polygon whoes number of points far exceeds the max number of points is rejected"
         [45.548821,52.267059,45.549019,52.265549,45.549126,52.265541,45.549698,52.265549,45.550209,52.265595,45.550774,52.26564,45.551346,52.265671,45.551933,52.265709,45.552536,52.265762,45.553124,52.265801,45.553711,52.265846,45.554306,52.265884,45.554901,52.26593,45.555496,52.265968,45.556374,52.266037,45.556969,52.266083,45.557556,52.266136,45.558136,52.266174,45.558701,52.266213,45.559563,52.266274,45.560135,52.266312,45.560715,52.26635,45.561302,52.26638,45.561874,52.266419,45.562752,52.266464,45.563332,52.266502,45.563896,52.266541,45.564423,52.266571,45.564926,52.266602,45.565872,52.266663,45.566399,52.266693,45.566895,52.266731,45.567032,52.266739,45.567085,52.266792,45.567055,52.266914,45.567047,52.267166,45.567062,52.267265,45.56707,52.26751,45.567078,52.267578,45.567085,52.267639,45.567078,52.267731,45.567085,52.267815,45.567116,52.268044,45.567123,52.268456,45.567131,52.268814,45.567116,52.26918,45.567108,52.269501,45.567123,52.269928,45.567139,52.27018,45.567146,52.270462,45.567146,52.270874,45.567139,52.271156,45.567139,52.2715,45.567146,52.27179,45.567162,52.272087,45.567177,52.272461,45.567162,52.272774,45.567146,52.273087,45.567146,52.273415,45.567223,52.273544,45.567268,52.273613,45.567299,52.273666,45.567291,52.273766,45.567291,52.273872,45.567276,52.274185,45.567268,52.274292,45.567268,52.274345,45.567238,52.274445,45.567238,52.274635,45.567253,52.274719,45.567253,52.274857,45.567238,52.275047,45.56723,52.275139,45.567238,52.275208,45.567238,52.275284,45.567238,52.275391,45.567246,52.275528,45.567238,52.275726,45.567246,52.275925,45.567238,52.276047,45.567215,52.276138,45.567215,52.276222,45.567207,52.276268,45.567253,52.276474,45.567284,52.276581,45.567352,52.276772,45.567368,52.276825,45.567352,52.276985,45.567329,52.2771,45.567329,52.277161,45.567352,52.277268,45.567413,52.277435,45.567429,52.277489,45.567406,52.277542,45.567337,52.277794,45.567314,52.277908,45.567291,52.278099,45.567307,52.278397,45.567307,52.278679,45.567284,52.278832,45.567177,52.279137,45.567154,52.279327,45.567154,52.279694,45.567162,52.280037,45.567177,52.280335,45.567184,52.280632,45.5672,52.280945,45.567207,52.280998,45.5672,52.281044,45.567192,52.281105,45.5672,52.281212,45.5672,52.281319,45.567184,52.281395,45.567184,52.28157,45.567192,52.281899,45.567192,52.282234,45.567192,52.282539,45.5672,52.282867,45.5672,52.28315,45.5672,52.28344,45.567215,52.283752,45.567215,52.284081,45.567215,52.284393,45.567276,52.284531,45.567307,52.284584,45.567307,52.284668,45.567268,52.284798,45.567261,52.284996,45.567276,52.285111,45.567299,52.285172,45.567307,52.285423,45.567268,52.285561,45.567253,52.285606,45.567238,52.285721,45.567238,52.286064,45.567238,52.286346,45.567238,52.286697,45.567238,52.287041,45.567253,52.287369,45.567253,52.287674,45.567246,52.287964,45.567246,52.288277,45.567246,52.28862,45.567253,52.288933,45.567261,52.289261,45.567268,52.289574,45.567284,52.289696,45.567246,52.289764,45.567238,52.289871,45.567253,52.290222,45.567261,52.29055,45.567268,52.290878,45.567284,52.291176,45.567291,52.291473,45.567307,52.291748,45.567322,52.292114,45.567337,52.292442,45.567352,52.292572,45.56736,52.292839,45.567352,52.292915,45.567284,52.292992,45.567131,52.293045,45.567032,52.293068,45.566765,52.293114,45.566261,52.29319,45.565765,52.293266,45.565277,52.293343,45.564804,52.293427,45.564323,52.293503,45.563873,52.293572,45.563286,52.293633,45.563004,52.293678,45.562874,52.293694,45.562744,52.293709,45.562614,52.293709,45.5625,52.293732,45.562386,52.293755,45.561897,52.293831,45.561806,52.293846,45.561562,52.293877,45.561363,52.293892,45.561165,52.293907,45.56102,52.293922,45.560883,52.293938,45.560738,52.293922,45.560654,52.293907,45.56057,52.293861,45.560486,52.293785,45.560432,52.293694,45.560387,52.293465,45.560379,52.293411,45.560364,52.293114,45.560356,52.29306,45.560364,52.293015,45.560371,52.292961,45.560402,52.292915,45.560509,52.292793,45.560562,52.292748,45.5607,52.292664,45.560967,52.292526,45.561073,52.292488,45.561348,52.292412,45.561432,52.292397,45.5616,52.292366,45.561844,52.292358,45.562149,52.292366,45.56263,52.292358,45.56295,52.292366,45.563362,52.292397,45.563438,52.292381,45.563461,52.292336,45.563454,52.29229,45.563454,52.292229,45.563438,52.292175,45.563355,52.29213,45.562851,52.292069,45.562355,52.292023,45.562012,52.291962,45.561905,52.291939,45.561684,52.291916,45.561127,52.291908,45.5606,52.291885,45.560486,52.291878,45.560394,52.29187,45.56031,52.29184,45.560234,52.291809,45.560173,52.291771,45.56012,52.291733,45.560043,52.291451,45.559982,52.291153,45.559967,52.291077,45.559929,52.291,45.559891,52.290932,45.559769,52.290817,45.559715,52.290772,45.559639,52.290741,45.559555,52.290733,45.559418,52.290726,45.559204,52.290718,45.559097,52.290718,45.558846,52.290726,45.558746,52.290726,45.558662,52.290718,45.558563,52.29071,45.558472,52.290703,45.55835,52.290703,45.558197,52.290718,45.558037,52.290733,45.557861,52.290741,45.557404,52.290779,45.556877,52.290825,45.556595,52.29084,45.556221,52.290886,45.556076,52.290894,45.55587,52.290886,45.555748,52.290878,45.555649,52.290855,45.555557,52.290817,45.555496,52.290764,45.555473,52.290695,45.555443,52.290619,45.555435,52.290573,45.55542,52.290527,45.555458,52.290367,45.555519,52.290047,45.555565,52.289711,45.555565,52.289597,45.555565,52.289536,45.555573,52.289352,45.55555,52.289116,45.555542,52.288948,45.555542,52.28878,45.555542,52.288719,45.555519,52.288384,45.555519,52.288284,45.55542,52.28801,45.555382,52.287888,45.555367,52.287819,45.555313,52.287613,45.555229,52.287422,45.555214,52.287369,45.555183,52.287315,45.555153,52.287262,45.555107,52.287132,45.554955,52.28682,45.55481,52.286552,45.554764,52.286476,45.554642,52.286285,45.554497,52.286003,45.554329,52.285706,45.554184,52.285431,45.55407,52.285149,45.554047,52.285095,45.554039,52.285049,45.554016,52.284996,45.553909,52.284721,45.553818,52.284401,45.553734,52.284126,45.553711,52.28405,45.55368,52.283989,45.553383,52.283951,45.553337,52.283997,45.553291,52.284042,45.553268,52.284119,45.553261,52.28421,45.553291,52.284493,45.553276,52.284607,45.553284,52.28476,45.553307,52.284874,45.553398,52.285202,45.553429,52.285339,45.55349,52.285538,45.55352,52.285652,45.553581,52.285843,45.553635,52.286041,45.553734,52.286377,45.553825,52.286659,45.553848,52.286728,45.553894,52.286949,45.554024,52.28727,45.554176,52.287598,45.554199,52.287659,45.554215,52.28772,45.554276,52.28788,45.554421,52.288162,45.554489,52.288284,45.55452,52.28833,45.554573,52.288467,45.554634,52.288788,45.554657,52.289116,45.554642,52.289215,45.554573,52.289528,45.554482,52.289818,45.554466,52.289864,45.554436,52.289909,45.55439,52.289948,45.554344,52.289986,45.554283,52.290016,45.554215,52.290047,45.554123,52.290062,45.554024,52.290077,45.553703,52.290108,45.553375,52.290108,45.553124,52.290108,45.552734,52.290131,45.552513,52.290161,45.552185,52.290222,45.552094,52.290237,45.55201,52.290245,45.551743,52.290337,45.551682,52.290367,45.551445,52.290535,45.551361,52.290596,45.55114,52.290749,45.550789,52.290962,45.550728,52.291,45.550674,52.291039,45.550644,52.291084,45.550613,52.291222,45.550606,52.291351,45.550606,52.29142,45.550598,52.291565,45.550575,52.291611,45.550499,52.291702,45.5504,52.291756,45.550331,52.291779,45.549988,52.29184,45.549683,52.291878,45.549164,52.291946,45.548782,52.291992,45.548279,52.292069,45.547791,52.292137,45.547524,52.292175,45.547035,52.292244,45.546944,52.292259,45.546837,52.292275,45.546761,52.292282,45.546677,52.292282,45.5466,52.292282,45.546524,52.292282,45.546288,52.292107,45.546242,52.292069,45.546249,52.291908,45.546265,52.291611,45.546295,52.291275,45.54631,52.29113,45.546326,52.290848,45.546333,52.29055,45.546333,52.290505,45.546349,52.290344,45.546372,52.290024,45.546387,52.289719,45.546455,52.289391,45.546509,52.289032,45.5466,52.288887,45.546639,52.288818,45.546661,52.28859,45.546677,52.288277,45.546661,52.288155,45.546661,52.288109,45.546616,52.287903,45.546616,52.287804,45.546639,52.287712,45.546646,52.287651,45.546646,52.287361,45.546684,52.287079,45.546745,52.286797,45.546753,52.286743,45.546753,52.28669,45.546761,52.286446,45.546753,52.286324,45.546745,52.286263,45.546738,52.286186,45.546768,52.285912,45.546768,52.285866,45.547035,52.284279,45.547043,52.284195,45.547043,52.284119,45.547043,52.284035,45.546967,52.283936,45.546944,52.283875,45.547012,52.283569,45.547073,52.283234,45.547127,52.282875,45.547157,52.282585,45.54718,52.282295,45.547218,52.281937,45.547287,52.281616,45.547386,52.281258,45.547417,52.281075,45.54744,52.280785,45.547478,52.280464,45.547508,52.280159,45.547531,52.279846,45.547554,52.279503,45.547592,52.279144,45.54763,52.278778,45.547669,52.27842,45.547691,52.278053,45.54773,52.277702,45.547745,52.277351,45.547745,52.277123,45.547707,52.276794,45.547676,52.276581,45.547676,52.276474,45.547707,52.276138,45.547768,52.27581,45.547821,52.275475,45.547867,52.275147,45.547882,52.274826,45.547905,52.274529,45.547905,52.274307,45.54792,52.274101,45.547928,52.273766,45.547943,52.273415,45.547958,52.273064,45.547997,52.272705,45.548035,52.272339,45.548088,52.27198,45.548157,52.271614,45.548218,52.271263,45.548279,52.270905,45.54834,52.270554,45.548401,52.27021,45.548454,52.269867,45.548515,52.269531,45.548546,52.269188,45.548584,52.268837,45.548615,52.268478,45.54866,52.26812,45.548706,52.267761,45.548767,52.26741,45.548821,52.267059]      
         [no-lr])))



