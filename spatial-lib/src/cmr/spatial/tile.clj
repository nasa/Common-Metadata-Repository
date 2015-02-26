(ns cmr.spatial.tile
  (:require [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rel]
            [clojure.java.io :as io]
            [cmr.spatial.derived :as d]
            [cmr.spatial.ring-relations :as rr]))
  			
     
(defprotocol TileOperations
  "Operations on tiles"
  (intersects? [this geom] "returns true if geometry of the tile intersects with geom")
  (coordinates [this] "returns the tile coordinates as list" ))


(defrecord ModisTile[h v geometry]
  TileOperations
  (intersects? [this geom]
               (rel/intersects-ring? geom (:geometry this)))
  (coordinates [this] [h v]))

(defn- read-modis-tiles 
  "Read Modis tiles from a text file"
  []
  (let [tiles (read-string(slurp (clojure.java.io/resource "cmr/spatial/modis_tiles.edn")))]
    (for [{:keys [h v coordinates]} tiles] 
      (->ModisTile h v (d/calculate-derived (apply rr/ords->ring :geodetic coordinates))))))

(def modis-tiles (vec (read-modis-tiles)))

(defn tiles-from-geometry
  "Get all modis tiles which intersect the given geometry"
  [geom]
  (filter #(not(nil? %))  
    (map #(if (intersects? % geom) (coordinates %) nil) modis-tiles))
)



