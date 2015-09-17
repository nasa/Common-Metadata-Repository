(ns cmr.umm-spec.spatial-util)

(defn point
  "Returns a point map with the given longitude and latitude."
  [lon lat]
  {:Longitude lon :Latitude lat})

(defn closed?
  "Returns true if first and last points in sequence represent the same coordinates."
  [points]
  (= (first points) (last points)))

(defn closed
  "Returns points such that the first point is equal to the last point."
  [points]
  (conj (vec points) (first points))) 

(defn open
  "Returns points such that the first point is not equal to the last point."
  [points]
  (vec (butlast points)))

(defn edges
  "Returns pairs of vertices in seq of points."
  [points]
  (partition 2 1 (closed points)))

(defn- distance
  [p1 p2]
  (let [{^double x1 :Longitude ^double y1 :Latitude} p1
       {^double x2 :Longitude ^double y2 :Latitude} p2]
    (* (- x2 x1) (+ y2 y1))))

(defn clockwise?
  "Determines if the point sequence is in clockwise order.
  Uses sum over the area under the edges solution as described here:
  http://stackoverflow.com/questions/1165647/how-to-determine-if-a-list-of-polygon-points-are-in-clockwise-order"
  [points]
  (let [^double sum (->> (edges points)
                         (map (fn [[p1 p2]] (distance p1 p2)))
                         (apply +))]
    (>= sum 0.0)))

(defn counterclockwise
  "Returns points in counter-clockwise order."
  [points]
  (if (clockwise? points)
    (reverse points)
    points))

(defn clockwise
  "Returns points in clockwise order."
  [points]
  (if (clockwise? points)
    points
    (reverse points)))

(defn umm-point-order
  "Returns points in UMM (closed and ccw) order."
  [points]
  (counterclockwise (closed points)))
