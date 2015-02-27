(ns cmr.spatial.test.tile
  (:require [clojure.test :refer :all]
            [cmr.spatial.geodetic-ring :as gr]
  			    [cmr.spatial.tile :as t]
     		    [cmr.spatial.ring-relations :as rr]
     		    [cmr.spatial.derived :as d]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.line-string :as l]))

(deftest modis-tile-coordinates
  (testing "creation and retrieval of modis tile coordinates"
    (let [tile (t/->ModisSinTile [7 0] nil)]
         (is (= [7 0] (:coordinates tile))))))

(deftest modis-tile-geometry-intersection
  (testing "testing bouding box intersection with a modis tile"
    (let [tile (t/->ModisSinTile [7 0] (d/calculate-derived 
                                    (apply rr/ords->ring :geodetic [0,0,10,0,10,10,0,10,0,0])))
          geom (d/calculate-derived (rr/ords->ring :geodetic 5 5,15 5,15 15,5 15,5 5))]
         (is (t/intersects? tile geom)))))

(deftest modis-search-overalapping-tiles
  (testing "find all the tiles which intersect the given geometry"
    (are [geom tiles] (= (set tiles) (set (t/geometry->tiles geom)))
         
         ;; A large bounding box near the equator
         (d/calculate-derived (m/mbr -20 20 20 -20))
         [[15 8] [15 9] [16 6] [16 7] [16 8] [16 9] [16 10] [16 11] [17 6] [17 7] [17 8] [17 9] 
          [17 10] [17 11] [18 6] [18 7] [18 8] [18 9] [18 10] [18 11] [19 6] [19 7] [19 8] [19 9] 
          [19 10] [19 11] [20 8] [20 9]]
         
         ;; A small geodetic ring completely inside a tile
         (d/calculate-derived (rr/ords->ring :geodetic -77.205, 39.112, -77.188, 39.134, -77.221, 
                                            39.143, -77.252, 39.130, -77.250,39.116,-77.205,39.112))
         [[12 5]]
         
         ;; A point 
         (p/point -84.2625 36.0133)
         [[11 5]]
         
         ;;Geodetic line string
         (l/ords->line-string :geodetic 1 1, 10 5, 15 9)
         [[18 8][19 8]]
         
         ;;A narrow bounding box crossing over two tiles
         (m/mbr -0.01 15.0 0.01 5.0)
         [[17 7][17 8][18 7][18 8]]
         
         ;; North Pole
         p/north-pole
         [[17 0] [18 0]]
         
         ;;South Pole
         p/south-pole
         [[17 17][18 17]]
         
         ;;Bounding box crossing anti-meridian
         (m/mbr 178.16 1.32 -176.79 -4.19)
         [[0 8][0 9][35 8][35 9]]
         
         ;;whole world
         (m/mbr -180 90 180 -90)
         (map :coordinates t/modis-sin-tiles)
    )))