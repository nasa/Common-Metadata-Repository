(ns cmr.spatial.tile
  "This contains functions to work with tiles defined in various 2D coordinate 
  system grids such as Modis Integerized Sinusoidal Grid."
  (:require [cmr.spatial.math :refer :all]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [clojure.java.io :as io]
            [cmr.spatial.derived :as d]
            [cmr.spatial.relations :as rel]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-segment :as s]))

(def ^:const ^:private ^:int H
  "The number of tiles along east-west in Modis Sinusoidal Grid"
  36)

(def ^:const ^:private ^:int V
  "The number of tiles along north-south in Modis Sinusoidal Grid" 
  18)

(def ^:const ^:private ^:float SIZE
  "Size of each of the two sides of a tile"
  (/ TAU H))

(def ^:const ^:private ^:float DENSIFICATION_DISTANCE
  ""
  (+ (/ SIZE 20) 0.00000001))
  			
;;Modis Sinusoidal Tile
(defrecord ModisSinTile
  [ 
   ;; tile coordinates as a vector in the format [h v]
   ;; h is the tile coordinate along east-west and has a range of 0-35
   ;; v is the tile coordinate along north-south and has a range of 0-17
   coordinates
   ;; Tile geometry as geodetic ring
   geometry])

(defn intersects?
  "Returns true if geometry of the tile intersects with the given geometry. geometry could
   be of any geometric type including point, line, polygon, mbr and ring"
  [tile geometry]
  (rel/intersects-ring? geometry (:geometry tile)))

(defn- find-tile-quadrant
  "The quadrant in which a tile with the given coordinates falls."
  [h v]
  (case (vector (< h (/ H 2)) (< v (/ V 2)))
    [false true] :ur
    [true true] :ul
    [true false] :ll
    [false false] :lr))

(defn- map-tile-to-ur-quadrant
  "Map the given tile to the 'equivalent' tile in the upper right quadrant."
  [h v]
  (case (find-tile-quadrant h v)
    :ur [h v]
    :ul [(- H h 1) v]
    :ll [(- H h 1) (- V v 1)]
    :lr [h (- V v 1)]))

(defn- map-tile-point-backwards
  "Maps a point on the edge of a tile in the upper-right quadrant back to its original quadrant."
  [[x y] quadrant]
  (case quadrant
    :ur [x y]
    :ul [(- x) y]
    :ll [(- x) (- y)]
    :lr [x (- y)]))

(defn- find-tile-vertices
  "Find the coordinates of the four vertices of a tile"
  [h v]
  (let [x_min (* (- h (/ H 2)) SIZE)
        y_min (* (- (/ V 2) (+ v 1)) SIZE)
        x_max (+ x_min SIZE)
        y_max (+ y_min SIZE)]
    [[x_max y_min] [x_max y_max] [x_min y_max] [x_min y_min]]))

(defn- max-x
  "Determine the largest x coordinate for a given y 
  coordinate so that [x  y] is not a fictional point"
  [y]
  (* (/ TAU 2) (cos y)))

(defn- max-y
  "Determine the largest y coordinate for a given x 
  coordinate so that [x  y] is not a fictional point"
  [x]
  (acos (/ x (/ TAU 2))))

(defn- fictional-point?
  "Determine if a point in the Sinusoidal grid maps to a real point on the earth"
  [x y]
  (or (> x (max-x y)) (> y (max-y x))))

(defn- degenerate_tile?
  "Determine if a tile is an edge case, i.e. only one vertex of the tile is not a 
  fictional point and rest of the points in the tile are fictional points."
  [y1 x4]
  (= x4 (max-x y1)))

(defmulti add-edge-point
  "Method to find 'edge-point' given a line segment joining two points exactly one of 
  which is a fictional point (in Sinusoidal projection). Edge points lie on 
  anti-meridian and they divide fictional points from real points on a tile edge."
  (fn [[x1 y1] [x2 y2] densified_segment] 
      (cond
         (= y1 y2) (if (< x1 x2) :left-right :right-left)
         (= x1 x2) (if (< y1 y2) :bottom-top :top-bottom))))

(defmethod add-edge-point :left-right
  [p1 [x2 y2] densified_segment]
  (conj densified_segment [(max-x y2) y2]))

(defmethod add-edge-point :bottom-top
  [[x1 y1] p2 densified_segment]
  (let [max_y1 (max-y x1)]
    ;;The if condition avoids duplicate points which occur when only real point on the line segment 
    ;;is an end-point. Same is true for the next function.
    (if (= y1 max_y1) densified_segment (conj densified_segment [x1 max_y1]))))

(defmethod add-edge-point :right-left
  [p1 [x2 y2] densified_segment]
  (let [max_x2 (max-x y2)]
    (if (= x2 max_x2) densified_segment (into [[max_x2 y2]] densified_segment))))

(defmethod add-edge-point :top-bottom
  [[x1 y1] p2 densified_segment]
  (into [[x1 (max-y x1)]] densified_segment))

(defn- coord
  "Find (x or y) coordinate of a point at a distance i/n of df from coordinate c.
  This is used to add (n - 1) new points along the line segment between two 
  points with a distance of df between them"
  [c i df n]
  (float (+ c (* df (/ i n)))))

(defn- densify-segment
  "Densify a line segment joining two input points"
  [[x1 y1] [x2 y2]]
  (let [df_x (- x2 x1)
        df_y (- y2 y1)
        df_dist (sqrt (+ (* df_x df_x) (* df_y df_y)))
        npts (inc (int (/ df_dist DENSIFICATION_DISTANCE)))
        inter_points (map #(vector (coord x1 % df_x npts) (coord y1 % df_y npts)) (range npts))]
    (into (vec inter_points) [[x2 y2]])))

(defn- densify-tile-edge
  "Densify a tile edge, remove fictional points and add edge points"
  [[p1 p2]]
  (let [densified_segment (->> (densify-segment p1 p2)
                               drop-last
                               (remove #(apply fictional-point? %))
                               vec)]
    (if (not= (apply fictional-point? p1) (apply fictional-point? p2))
      (add-edge-point p1 p2 densified_segment) densified_segment)))

(defn- densify-tile
  "Densify the bounding rectangle corresponding to a tile by adding new points and 
  removing fictional points. The input vertices are assumed to be in anti-clockwise order."
  [vertices]
  (reduce into [] (map densify-tile-edge (partition 2 1 [(first vertices)] vertices)))) 
  
(defn- planar->geodetic
  "Maps a point in the Sinusoidal projection to a corresponding geodetic coordinate"
  [x y]
  (let [authalic_lat y
        cos_lat (cos y)
        authalic_lon (if (= cos_lat 0) 0 (/ x cos_lat))]
    (map degrees [authalic_lon authalic_lat])))

(defn- compute-geometry
  "Computes the geometry of a Modis Sinusoidal Tile as a vector of geodetic coordinates 
  representing the ring corresponding to the boundary of the tile."
  [h v]
  (let [quadrant (find-tile-quadrant h v)
        [h_ur v_ur] (map-tile-to-ur-quadrant h v)
        [p1 p2 p3 p4] (find-tile-vertices h_ur v_ur)
        [x1 y1] p1
        [x4 y4] p4]
    (if-not (or (fictional-point? x4 y4) (degenerate_tile? y1 x4))
      (->> (densify-tile [p1 p2 p3 p4])
           (#(conj % (first %)))
           (map #(map-tile-point-backwards % quadrant))
           (map #(apply planar->geodetic %))
           (#(if (or (= quadrant :ul) (= quadrant :lr)) (reverse %) %))
           flatten))))

;; Computation of a Modis tile coordinates follows the steps below. (Please See: 
;; https://wiki.earthdata.nasa.gov/display/CMR/Computation+of+MODIS+Tile+Geometry)
;; 1) Find if the tile is a fictional tile or a real tile or a degenerate tile. Ignore fictional 
;;    and degenerate tiles. Degenerate tiles have only one vertex which is a real point. All the 
;;    remaining points of the tile are fictional points.
;; 2) Map the tile to an equivalent tile in the upper-right quadrant(UR).
;; 3) Find the coordinates of the four vertices of the rectangle corresponding to the tile.
;; 4) Densify the each of the four segments of the tile.
;; 5) Remove fictional points on each of the edges if there are any.
;; 6) Add the edge-points if the edge has fictional points on one side and real points on another.
;; 7) Map the densified points back to its original quadrant.
;; 8) Convert the points from planar coordinates to geodetic coordinates. 
;;    Convert from radians to degrees.

(def modis-sin-tiles 
  "A vector consisting of all ModisSinTile records"
  (delay (vec (keep identity 
                    (for [h (range H)
                          v (range V)]
                      (let [ords (map #(round 4 %) (compute-geometry h v))]
                        (if (not(empty? ords))
                          (->ModisSinTile 
                            (vector h v)
                            (d/calculate-derived (apply rr/ords->ring :geodetic ords))))))))))

(defn geometry->tiles
  "Gets tiles which intersect the given geometry as a sequence of tuples, each tuple 
  being a vector holding two entries in the format [h v]. geometry could be of 
  any geometric type including point, line, polygon, mbr and ring"
  [geometry]
  (let [geometry (d/calculate-derived geometry)]
    (keep  #(when (intersects? % geometry) (:coordinates %)) @modis-sin-tiles)))