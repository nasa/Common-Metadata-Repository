(ns cmr.spatial.tile
  "This contains functions to work with tiles defined in various 2D coordinate
  system grids such as Modis Integerized Sinusoidal Grid.

  The tile geometry is computed as necessary. Currently only computation of MODIS Sinusoidal tiles
  is implemented. Computation of tile coordinates for a Modis tile follows the steps below.
  (Please See: https://wiki.earthdata.nasa.gov/display/CMR/Computation+of+MODIS+Tile+Geometry)
  1) Map the tile to an equivalent tile in the upper-right quadrant(UR).
  2) Find if the mapped tile is a fictional tile or a real tile or a degenerate tile. Ignore
     fictional and degenerate tiles. Degenerate tiles have only one vertex which is a real point.
     All the remaining points of the tile are fictional points.
  3) Find the coordinates of the four vertices of the rectangle corresponding to the mapped tile.
  4) Densify each of the four segments of the tile mapped.
  5) Remove fictional points on each of the edges if there are any.
  6) Add the edge-point if the edge has fictional points on one side and real points on another.
  7) Map the densified points back to its original quadrant.
  8) Convert the points from planar coordinates to geodetic coordinates. Convert from radians to
     degrees."
  (:require
   [clojure.java.io :as io]
   [cmr.spatial.derived :as d]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-segment :as s]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.point :as p]
   [cmr.spatial.relations :as rel]
   [cmr.spatial.ring-relations :as rr]
   [primitive-math]))

(primitive-math/use-primitive-operators)

(def ^:const ^:private ^:int NUM_HORIZONTAL_TILES
  "The number of tiles along east-west in Modis Sinusoidal Grid."
  36)

(def ^:const ^:private ^:int NUM_VERTICAL_TILES
  "The number of tiles along north-south in Modis Sinusoidal Grid."
  18)

(def ^:const ^:private ^:double TILE_SIZE
  "Size of each of the four edges of a tile projected off a unit sphere. The
  need for radius of earth in computations, which is cancelled out anyway, is
  avoided by using a unit sphere. The units of this parameter is radians."
  (/ TAU (double NUM_HORIZONTAL_TILES)))

(def ^:const ^:private ^:int NUM_DENSIFICATION_SEGMENTS
  "The number of segments into which each edge of a tile is partitioned in
  the projected coordinate system during densification"
  20)

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
  "Returns true if geometry of the tile intersects with the given geometry. Geometry could
   be of any type including point, line, polygon, mbr and ring"
  [tile geometry]
  (rel/intersects-ring? geometry (:geometry tile)))

(defn- find-tile-quadrant
  "The quadrant in which a tile with the given tile coordinates falls."
  [^long h ^long v]
  (case [(< h (/ NUM_HORIZONTAL_TILES 2)) (< v (/ NUM_VERTICAL_TILES 2))]
    [false true] :ur
    [true true] :ul
    [true false] :ll
    [false false] :lr))

(defn- tile->ur-quadrant-tile
  "Map the given tile to the 'equivalent' tile in the upper right quadrant. The symmetry
  of the tiles along the equator and central meridian is used to reduce the number of
  conditional checks during computation of coordinates along tile edges."
  [^long h ^long v]
  (case (find-tile-quadrant h v)
    :ur [h v]
    :ul [(- NUM_HORIZONTAL_TILES h 1) v]
    :ll [(- NUM_HORIZONTAL_TILES h 1) (- NUM_VERTICAL_TILES v 1)]
    :lr [h (- NUM_VERTICAL_TILES v 1)]))

(defn- ur-quadrant-point->point
  "Maps a point in the upper-right quadrant back to its original quadrant."
  [[^double x ^double y] quadrant]
  (case quadrant
    :ur [x y]
    :ul [(- x) y]
    :ll [(- x) (- y)]
    :lr [x (- y)]))

(defn- tile->corner-vertices
  "Find the coordinates of the four vertices of a tile. The origin of the coordinate system
  used is at the centre of the grid, i.e. intersection of equator and meridian and NOT the
  top left corner. The function returns a vector of tuples, each tuple being a
  pair of coordinates for a vertex. The vertices are put in anti-clockwise order with
  lower-right vertex of the tile as the first vertex."
  [^long h ^long v]
  (let [x-min (* (double (- h (/ NUM_HORIZONTAL_TILES 2))) TILE_SIZE)
        y-min (* (double (- (/ NUM_VERTICAL_TILES 2) (inc v))) TILE_SIZE)
        x-max (+ x-min TILE_SIZE)
        y-max (+ y-min TILE_SIZE)]
    [[x-max y-min] [x-max y-max] [x-min y-max] [x-min y-min]]))

(defn- bound
  "Return value if absolute value of value is less than max-value otherwise return max-value
  if value is greater than 0 and -max-value if value is less than 0."
  [^double value ^double max-value]
  (if (< (abs value) max-value)
      value
         (if (< value 0.) (- max-value) max-value)))

(defn- max-x-for-y
  "Determine the largest x coordinate for a given y coordinate so that
  [max-x-for-y  y] is an edge-point"
  ^double [^double y]
  (* (/ TAU 2.) (cos y)))

(defn- max-y-for-x
  "Determine the largest y coordinate for a given x
  coordinate so that [x  max-y-for-x] is not a fictional point"
  ^double [^double x]
  (acos (bound (/ x (/ TAU 2.)) 1.)))

(defn- fictional-point?
  "Determine if a point in the Sinusoidal grid maps to a real point on the earth"
  [^double x ^double y]
  (or (> x (max-x-for-y y)) (> y (max-y-for-x x))))

(defn- degenerate-tile?
  "Determine if a tile is an edge case, i.e. only one vertex of the tile is not a
  fictional point and rest of the points in the tile are fictional points."
  [y1 x4]
  (= x4 (max-x-for-y y1)))

(defmulti add-edge-point
  "Method to find 'edge-point' given a line segment joining two points exactly one of
  which is a fictional point (in Sinusoidal projection). Edge points lie on
  anti-meridian and they divide fictional points from real points on a tile edge.
  The function arguments are coordinates of the two points and a vector of points holding the
  densified segement between the two points from which all fictional points are removed."
  (fn [[^double x1 ^double y1] [^double x2 ^double y2] densified-segment]
      (cond
         (= y1 y2) (if (< x1 x2) :left-right :right-left)
         (= x1 x2) (if (< y1 y2) :bottom-top :top-bottom)
         :else (throw (Exception. "Either x or y-coordinates of the given points must be equal")))))

(defmethod add-edge-point :left-right
  [p1 [x2 y2] densified-segment]
  (concat densified-segment [[(max-x-for-y y2) y2]]))

(defmethod add-edge-point :bottom-top
  [[x1 y1] p2 densified-segment]
  (let [max-y1 (max-y-for-x x1)]
    ;;The if condition avoids duplicate points which occur when only real point on the line segment
    ;;is an end-point. Same is true for the next function.
    (if (approx= y1 max-y1 DELTA)
      densified-segment
      (concat densified-segment [[x1 max-y1]]))))

(defmethod add-edge-point :right-left
  [p1 [x2 y2] densified-segment]
  (let [max-x2 (max-x-for-y y2)]
    (if (approx= x2 max-x2 DELTA)
      densified-segment
      (concat [[max-x2 y2]] densified-segment))))

(defmethod add-edge-point :top-bottom
  [[x1 y1] p2 densified-segment]
  (concat [[x1 (max-y-for-x x1)]] densified-segment))

(defn- coord
  "Find (x or y) coordinate of a point at a distance i/num-points of dist from the coordinate
  ord along the axis of ord. This is used to add (num-points - 1) new points along the line segment
  between two points."
  [^double ord ^double i ^double dist ^double num-points]
  (+ ord (* dist (/ i num-points))))

(defn- densify-segment
  "Densify a line segment joining two input points."
  [[^double x1 ^double y1] [^double x2 ^double y2]]
  (let [df-x (- x2 x1)
        df-y (- y2 y1)]
    (map #(vector (coord x1 % df-x NUM_DENSIFICATION_SEGMENTS)
                  (coord y1 % df-y NUM_DENSIFICATION_SEGMENTS))
         (range (inc NUM_DENSIFICATION_SEGMENTS)))))

(defn- densify-tile-edge
  "Densify a tile edge, remove fictional points and add edge point if necessary"
  [[p1 p2]]
  (let [densified-segment (->> (densify-segment p1 p2)
                               drop-last
                               (remove (partial apply fictional-point?)))]
    (if (not= (apply fictional-point? p1) (apply fictional-point? p2))
      (add-edge-point p1 p2 densified-segment) densified-segment)))

(defn- densify-tile
  "Densify the bounding rectangle corresponding to a tile by adding new points and
  removing fictional points. The input vertices are assumed to be in anti-clockwise order."
  [vertices]
  (mapcat densify-tile-edge (partition 2 1 [(first vertices)] vertices)))

(defn- planar->geodetic
  "Maps a point in the Sinusoidal projection to a corresponding geodetic coordinate"
  [^double x ^double y]
  (let [authalic-lat y
        cos-lat (cos y)
        authalic-lon (if (= cos-lat 0.) 0. (/ x cos-lat))]
    [(bound (degrees authalic-lon) 180.) (bound (degrees authalic-lat) 90.)]))

(defn- tile->geometry
  "Computes the geometry of a Modis Sinusoidal Tile as a vector of geodetic coordinates
  representing the ring corresponding to the boundary of the tile."
  [h v]
  (let [quadrant (find-tile-quadrant h v)
        [h-ur v-ur] (tile->ur-quadrant-tile h v)
        [p1 p2 p3 p4] (tile->corner-vertices h-ur v-ur)
        [x1 y1] p1
        [x4 y4] p4]
    (if-not (or (fictional-point? x4 y4) (degenerate-tile? y1 x4))
      (->> (densify-tile [p1 p2 p3 p4])
           (#(concat % [(first %)]))
           (map #(ur-quadrant-point->point % quadrant))
           (map (partial apply planar->geodetic))
           (#(if (or (= quadrant :ul) (= quadrant :lr)) (reverse %) %))
           flatten))))

(def modis-sin-tiles
  "A vector consisting of all ModisSinTile records"
  ;; FIXME: We use delay here to postpone the evaluation of code inside delay until runtime when it
  ;; is dereffed. This was needed since without delay, we were seeing an unexpected error during
  ;; evaluation of the code during dynamic loading:
  ;; No implementation of method: :calculate-derived of protocol:
  ;; #'cmr.spatial.derived/DerivedCalculator found for class: cmr.spatial.geodetic_ring.GeodeticRing
  ;; The problems seems to be the presence of multiple versions of the same record class(GeodeticRing)
  ;; Investigation of the issue did not result in a fix for the issue till now.
  ;; Filed an issue to identify what is causing the compilation error: CMR-1306. (DU 03/17/2015)
  (delay (vec (keep identity
                    (for [h (range NUM_HORIZONTAL_TILES)
                          v (range NUM_VERTICAL_TILES)]
                      (when-let [ords (tile->geometry h v)]
                        (->ModisSinTile
                            (vector h v)
                            (d/calculate-derived (rr/ords->ring :geodetic ords)))))))))

(defn geometry->tiles
  "Gets tiles which intersect the given geometry as a sequence of tuples, each tuple
  being a vector holding two entries in the format [h v]. geometry could be of any type
  including point, line, polygon, mbr and ring"
  [geometry]
  (let [geometry (d/calculate-derived geometry)]
    (keep  #(when (intersects? % geometry) (:coordinates %)) @modis-sin-tiles)))

(defn all-tiles
  "Gets all MODIS Sinusoidal Tiles as a vector of tuples, each tuple being in the format [h v]
  where h is the column and v is the row of a tile in the MODIS Sinusoidal grid."
  []
  (map :coordinates @modis-sin-tiles))
