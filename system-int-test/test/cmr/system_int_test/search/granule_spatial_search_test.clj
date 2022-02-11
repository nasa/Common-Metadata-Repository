(ns cmr.system-int-test.search.granule-spatial-search-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.spatial.codec :as codec]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.messages :as smsg]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
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
  (let [geodetic-coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                                                                :EntryTitle "E1"
                                                                :ShortName "S1"
                                                                :Version "V1"}))
        cartesian-coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:SpatialExtent (data-umm-c/spatial {:gsr "CARTESIAN"})
                                                                 :EntryTitle "E2"
                                                                 :ShortName "S2"
                                                                 :Version "V2"}))
        geodetic-coll-cid (get-in geodetic-coll [:concept-id])
        cartesian-coll-cid (get-in cartesian-coll [:concept-id])
        make-gran (fn [ur & shapes]
                    (let [shapes (map (partial umm-s/set-coordinate-system :geodetic) shapes)]
                      (d/ingest "PROV1" (dg/granule-with-umm-spec-collection geodetic-coll geodetic-coll-cid
                                                    {:granule-ur ur
                                                     :spatial-coverage (apply dg/spatial shapes)}))))
        make-cart-gran (fn [ur & shapes]
                         (let [shapes (map (partial umm-s/set-coordinate-system :cartesian) shapes)]
                           (d/ingest "PROV1" (dg/granule-with-umm-spec-collection cartesian-coll cartesian-coll-cid
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

    (testing "valid circle searches"
      (are [lon-lat-radius items]
        (let [found (search/find-refs :granule {:circle lon-lat-radius
                                                :provider "PROV1"})
              matches? (d/refs-match? items found)]
          (when-not matches?
            (println "Expected:" (->> items (map :granule-ur) sort pr-str))
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
        ["0,89,1000000"] [whole-world north-pole on-np touches-np very-tall-cart]

        ;; cross antimeridian
        ["179.8,41,100000"] [whole-world across-am-poly]
        ["-179.9,22,100000"] [whole-world am-point]

        ;; multiple circles are ANDed together
        ["0,89,100" "0,89,1000000"] [whole-world on-np]
        ["0,0,1000" "0,89,1000" "0,89,1000000"] [whole-world]))

  (testing "ORed spatial search"
     (let [poly-coordinates ["-16.79,-12.71,-6.32,-10.95,-5.74,-6.11,-15.18,-7.63,-16.79,-12.71"
                             "0.53,39.23,21.57,59.8,-112.21,84.48,-13.37,40.91,0.53,39.23"]
           poly-refs (search/find-refs
                      :granule
                      {:provider "PROV1"
                       :polygon poly-coordinates
                       "options[spatial][or]" "true"})
           bbox-refs (search/find-refs
                             :granule
                             {:provider "PROV1"
                              :bounding-box ["166.11,-19.14,-166.52,53.04"
                                             "23.59,-15.47,25.56,-4"]
                                             "options[spatial][or]" "true"})
           combined-refs (search/find-refs
                          :granule
                          {:provider "PROV1"
                           :circle "179.8,41,100000"
                           :bounding-box "166.11,-19.14,-166.52,53.04"
                           "options[spatial][or]" "true"})
           anded-refs (search/find-refs
                       :granule
                       {:provider "PROV1"
                        :circle "179.8,41,100000"
                        :bounding-box "166.11,-19.14,-166.52,53.04"
                        "options[spatial][or]" "false"})]
      (is (d/refs-match? [across-am-poly whole-world across-am-br am-point very-wide-cart]
                         combined-refs))
      (is (d/refs-match? [wide-north on-np normal-poly very-wide-cart whole-world normal-brs]
                         poly-refs))
      (is (d/refs-match? [across-am-poly very-wide-cart am-point normal-line-cart whole-world across-am-br]
                         bbox-refs))
      (is (d/refs-match? [across-am-poly whole-world]
                         anded-refs))))

    (testing "ORed spatial search with other search params"
      (is (d/refs-match? [am-point]
                         (search/find-refs
                          :granule
                          {:provider "PROV1"
                           :granule-ur "am-point"
                           :circle "179.8,41,100000"
                           :bounding-box "166.11,-19.14,-166.52,53.04"
                           "options[spatial][or]" "true"}))))

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
  (let [geodetic-coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})}))
        geodetic-coll-cid (get-in geodetic-coll [:concept-id])
        make-gran (fn [ur & shapes]
                    (let [shapes (map (partial umm-s/set-coordinate-system :geodetic) shapes)]
                      (d/ingest "PROV1" (dg/granule-with-umm-spec-collection geodetic-coll geodetic-coll-cid
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
       [no-lr])))

(deftest circle-parameter-validation
  (testing "invalid circle parameters"
    (are3 [params error-msgs]
      (let [{:keys [status errors]} (search/find-refs :granule {:circle params
                                                                :provider "PROV1"})]
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
