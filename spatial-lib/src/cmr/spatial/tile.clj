(ns cmr.spatial.tile
  (:require [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [clojure.java.io :as io]
            [cmr.spatial.derived :as d]
            [cmr.spatial.relations :as rel]))
  			
;;Modis Sinusoidal Tile
(defrecord ModisSinTile
  [
   ;; Tile coordinate along east-west(0-35)
   h 
   ;; Tile coordinate along north-south(0-17)
   v 
   ;; Tile geometry as geodetic ring
   geometry])

(defn intersects?
  "Returns true if geometry of the tile intersects with the given geometry. geometry could
   be of any geometric type including point, line, polygon, mbr and ring"
  [tile geometry]
  (rel/intersects-ring? geometry (:geometry tile)))

(defn coordinates
  "Returns the tile coordinates as a vector"
  [tile]
  (vector (:h tile) (:v tile)))

(def modis-sin-tiles 
  "A vector of ModisTile records read from an edn file"
  (vec (let [tiles (read-string (slurp (clojure.java.io/resource "cmr/spatial/modis_tiles.edn")))]
            (for [{:keys [h v coordinates]} tiles] 
                (->ModisSinTile h v (d/calculate-derived 
                                 (apply rr/ords->ring :geodetic coordinates)))))))

(defn geometry->tiles
  "Gets tiles which intersect the given geometry as a sequence of tuples, each tuple being a vector 
   holding two entries in the format [h v]. geometry could be of any geometric type including 
   point, line, polygon, mbr and ring"
  [geometry]
  (keep  #(when (intersects? % geometry) (coordinates %)) modis-sin-tiles))