(ns cmr.spatial.test.tile
  (:require [clojure.test :refer :all]
            [cmr.spatial.geodetic-ring :as gr]
  			    [cmr.spatial.tile :as t]
     		    [cmr.spatial.ring-relations :as rr]
     		    [cmr.spatial.derived :as d]))

(deftest modis-tile-coordinates
  (testing "creation and retrieval of modis tile coordinates"
    (let [tile (t/->ModisTile 7 0 nil)]
      	 (do
      	 (is (= 7 (:h tile)))
         (is (= 0 (:v tile)))
         (is (= [7 0] (t/coordinates tile)))))))

(deftest modis-tile-geometry-intersection
  (testing "testing bouding box intersection with a modis tile"
    (let [tile (t/->ModisTile 7 0 (d/calculate-derived (apply rr/ords->ring :geodetic [0,0,10,0,10,10,0,10,0,0])))
          geom (d/calculate-derived (rr/ords->ring :geodetic 5 5,15 5,15 15,5 15,5 5))]
         (is (t/intersects? tile geom)))))

(deftest modis-search-overalapping-tiles
  (testing "find all the tiles which intersect the given geometry"
    (let [geom (d/calculate-derived (rr/ords->ring :geodetic -20,-20,20,-20,20,20,-20,20,-20,-20))]
         (is (= (t/tiles-from-geometry geom) [[15 8] [15 9] [16 6] [16 7] [16 8] [16 9] [16 10] [16 11] [17 6] [17 7] [17 8] [17 9] [17 10] [17 11] [18 6] [18 7] [18 8] [18 9] [18 10] [18 11] [19 6] [19 7] [19 8] [19 9] [19 10] [19 11] [20 8] [20 9]])))))

