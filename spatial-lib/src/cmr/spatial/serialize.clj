(ns cmr.spatial.serialize
  "Contains functions for serializing ordinates for storage in an index.

  This converts shapes into two sets of fields containing integers. The fields are:

  * ords - A list of 'ordinates' which is a sequence containing longitude latitude pairs flattened.
    * i.e. lon1, lat1, lon2, lat2, lon3, lat3 ...
    * All of the ordinates from all of the shapes for a given granule will be stored in the same
      ordinates list concatenated together.
  * ords-info - A list of pairs (flattened) of integers that contain type and size. Each pair
    represents one shape in the ords list.
    * type - an integer representing the spatial type (polygon, mbr, etc)
    * size - the number of ordinates in the ords list this shape uses."
  (:require [cmr.common.services.errors :as errors]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.math :refer :all]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.lr-binary-search :as lr]
            [clojure.set :as set]))


;; Some thoughts about how to store the elasticsearch data in a way that preserves space and accuracy.
(comment
  ;; 10 meters of accuracy at equator is provided by 0.0001 degrees
  ;; 123.4567 is the space of something.
  ;; Multiplying * 10000
  (== (* 123.4567 10000) 1234567)

  ;; Short is too small
  ;                1234567
  (= Short/MAX_VALUE 32767)

  ;; Integer is big enough and then some
  ;                       1234567
  (= Integer/MAX_VALUE 2147483647)

  (defn maintains-precision?
    [v]
    (= v
       (stored->ordinate (ordinate->stored v))))

  ;; We could store the largest longitude as an Integer
  (< (ordinate->stored 180) Integer/MAX_VALUE)
  (> (ordinate->stored -180) Integer/MIN_VALUE)

  ;; The maximum accuracy we could maintain is 7 digits
  (= true (maintains-precision? 179.1234567))
  (= true (maintains-precision? -179.1234567))

  ;; Any thing after that loses precision
  (= false (maintains-precision? 179.12345678)))

(def ^:const ^double multiplication-factor
  "Ordinates are stored as integers in elasticsearch to maintain space. This is the number used
  to convert the ordinate to and from an integer such that the largest ordinate fits in integer
  space and maintains as much accuracy as possible"
  10000000.0)

(defn ordinate->stored
  "Converts an ordinate value into an integer for storage"
  ^long [^double v]
  (Math/round (* v multiplication-factor)))

(defn stored->ordinate
  "Converts an stored ordinate value into a double for spatial calculations"
  [v]
  (double (/ v multiplication-factor)))

(defprotocol SerializeConversions
  (shape->stored-ords
    [shape]
    "Converts a spatial shape into ta list of maps containing a shape type and ordinates  to store in the search index")
  (shape->mbr
    [shape]
    "Converts a spatial shape into it's minimum bounding rectangle")
  (shape->lr
    [shape]
    "Determins the largest interior rectangle of a shape"))

(extend-protocol SerializeConversions

  cmr.spatial.polygon.Polygon

  (shape->stored-ords
    [{:keys [coordinate-system rings]}]
    ;; Performance enhancement: reduce size of stored rings and polygons by dropping the duplicate last two ordinates of a ring
    (let [polygon-type (keyword (str (name coordinate-system) "-polygon"))
          hole-type (keyword (str (name coordinate-system) "-hole"))]
      (concat
        [{:type polygon-type
          :ords (map ordinate->stored (rr/ring->ords (first rings)))}]
        ;; holes
        (map (fn [r]
               {:type hole-type
                :ords (map ordinate->stored (rr/ring->ords r))})
             (drop 1 rings)))))

  (shape->mbr
    [{:keys [mbr]}]
    mbr)

  (shape->lr
    [polygon]
    (lr/find-lr polygon))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.mbr.Mbr

  (shape->stored-ords
    [{:keys [west north east south]}]
    [{:type :br
      :ords (map ordinate->stored [west north east south])}])

  (shape->mbr
    [br]
    br)

  (shape->lr
    [br]
    br)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.point.Point

  (shape->stored-ords
    [{:keys [lon lat]}]
    [{:type :point
      :ords [(ordinate->stored lon) (ordinate->stored lat)]}])

  (shape->mbr
    [point]
    (m/point->mbr point))

  (shape->lr
    [point]
    (m/point->mbr point))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.spatial.line_string.LineString

  (shape->stored-ords
    [line]
    [{:type (keyword (str (name (:coordinate-system line)) "-line-string"))
      :ords (map ordinate->stored (l/line-string->ords line))}])

  (shape->mbr
    [line]
    (:mbr line))

  (shape->lr
    [line]
    ;; Performance enhancement: If a line has a vertical or horizontal arc we could use that to define
    ;; an LR that would be larger than just a point
    (m/point->mbr (first (:points line)))))


(defmulti stored-ords->shape
  "Converts a type and stored ordinates into a spatial shape"
  (fn [type ords]
    type))

(defmethod stored-ords->shape :geodetic-polygon
  [type ords]
  (poly/polygon :geodetic [(apply rr/ords->ring :geodetic (map stored->ordinate ords))]))

(defmethod stored-ords->shape :cartesian-polygon
  [type ords]
  (poly/polygon :cartesian [(apply rr/ords->ring :cartesian (map stored->ordinate ords))]))

(defmethod stored-ords->shape :geodetic-hole
  [type ords]
  (apply rr/ords->ring :geodetic (map stored->ordinate ords)))

(defmethod stored-ords->shape :cartesian-hole
  [type ords]
  (apply rr/ords->ring :cartesian (map stored->ordinate ords)))

(defmethod stored-ords->shape :br
  [type ords]
  (apply m/mbr (map stored->ordinate ords)))

(defmethod stored-ords->shape :point
  [type ords]
  (apply p/point (map stored->ordinate ords)))

(defmethod stored-ords->shape :geodetic-line-string
  [type ords]
  (apply l/ords->line-string :geodetic (map stored->ordinate ords)))

(defmethod stored-ords->shape :cartesian-line-string
  [type ords]
  (apply l/ords->line-string :cartesian (map stored->ordinate ords)))

(def shape-type->integer
  "Converts a shape type into an integer for storage"
  {:geodetic-polygon 1
   ;; A hole will immediately follow the shape which has the hole
   :geodetic-hole 2
   :br 3
   :point 4
   :geodetic-line-string 5
   :cartesian-polygon 6
   :cartesian-hole 7
   :cartesian-line-string 8})

(def integer->shape-type
  "Map of shape type integers to the equivalent keyword type"
  (set/map-invert shape-type->integer))

(defn shapes->ords-info-map
  "Converts a sequence of shapes into a map contains the ordinate values and ordinate info"
  [shapes]
  (let [;; Convert each shape into a map of types and ordinates
        infos (mapcat shape->stored-ords shapes)]

    {;; Create the ords-info sequence which is a sequence of types followed by the number of ordinates in the shape
     :ords-info (mapcat (fn [{:keys [type ords]}]
                            [(shape-type->integer type) (count ords)])
                          infos)
        ;; Create a combined sequence of all the shape ordinates
     :ords (mapcat :ords infos)}))

(defn ords-info->shapes
  "Converts the ords-info data and ordinates into a sequence of shapes"
  [ords-info ords]
  (loop [ords-info-pairs (partition 2 ords-info) ords ords shapes []]
    (if (empty? ords-info-pairs)
      shapes
      (let [[int-type size] (first ords-info-pairs)
            type (integer->shape-type int-type)
            _ (when-not type
                (errors/internal-error!
                  (format "Could not get a shape type from integer [%s]. Ords info: %s"
                          int-type (pr-str ords-info))))
            shape-ords (take size ords)
            shape (stored-ords->shape type shape-ords)
            ;; If shape is a hole add it to the last shape
            shapes (if (or (= type :geodetic-hole) (= type :cartesian-hole))
                     (update-in shapes [(dec (count shapes)) :rings] conj shape)
                     (conj shapes shape))]
        (recur (rest ords-info-pairs)
               (drop size ords)
               shapes)))))


;; This comment left in to show how to test the performance of a spatial search intersection between
;; a search area and one possible area.
(comment

  (do
    (def search-area (m/mbr 2.8125 26.015625 27.5625 18.5625))

    (require '[cmr.spatial.relations :as r])
    (def intersects-fn (r/shape->intersects-fn search-area))

    (def ords [105.430611,-81.285156 46.711628,-65.434303 34.878036,-45.048115 28.735548,-24.12281 23.99316,-3.005745 19.420183,18.155376 14.006246,39.221821 5.132201,59.967354 1.0E-4,66.7660068 1.0E-4,65.0446639 3.979038,59.776619 13.241453,39.078468 18.792515,18.024538 23.394991,-3.134466 28.082237,-24.256086 34.040928,-45.200386 45.345764,-65.661354 104.596161,-81.881721 180.0,-70.784953 180.0,-70.300547 105.430611,-81.285156])

    (def stored-ords
      (first (shape->stored-ords (poly/polygon :geodetic [(apply rr/ords->ring :geodetic ords)]))))

    (require '[criterium.core :refer [with-progress-reporting bench]]))

  (with-progress-reporting
    (bench
      (let [{:keys [type ords]} stored-ords
            shape (stored-ords->shape type ords)]
        (intersects-fn shape))))

  )

