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
            [cmr.spatial.ring :as r]
            [cmr.spatial.math :refer :all]
            [cmr.spatial.polygon :as poly]
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

(def multiplication-factor
  "Ordinates are stored as integers in elasticsearch to maintain space. This is the number used
  to convert the ordinate to and from an integer such that the largest ordinate fits in integer
  space and maintains as much accuracy as possible"
  10000000)

(defn ordinate->stored
  "Converts an ordinate value into an integer for storage"
  [v]
  (int (round 0 (* v multiplication-factor))))

(defn stored->ordinate
  "Converts an stored ordinate value into a double for spatial calculations"
  [v]
  (double (/ v multiplication-factor)))

(defprotocol SerializeConversions
  (shape->stored-ords
    [shape]
    "Converts a spatial shape into the ordinates to ordinates to store in the search index")
  (shape->mbr
    [shape]
    "Converts a spatial shape into it's minimum bounding rectangle")
  (shape->lr
    [shape]
    "Determins the largest interior rectangle of a shape"))

(extend-protocol SerializeConversions

  cmr.spatial.polygon.Polygon

  (shape->stored-ords
    [{:keys [rings]}]
    ;; only supports single ring polygons for now
    (when (> (count rings) 1)
      (errors/internal-error! "shape->stored-ords only supports polygons with a single ring. TODO add support"))

    ;; TODO reduce size of stored rings and polygons by dropping the duplicate last two ordinates of a ring

    {:type :polygon
     :ords (map ordinate->stored (r/ring->ords (first rings)))})

  (shape->mbr
    [{:keys [mbr]}]
    mbr)

  (shape->lr
    [{:keys [rings]}]
    ;; TODO this does not yet take into account holes
    (when (> (count rings) 1)
      (errors/internal-error! "Finding LR of polygon with holes is not supported yet."))
    (if-let [lr (lr/find-lr (first rings))]
      lr
      (errors/internal-error!
        (format "Unable to find lr of ring [%s]. The current LR algorithm is limited and needs to be improved."
                (pr-str (first rings)))))))


(defmulti stored-ords->shape
  "Converts a type and stored ordinates into a spatial shape"
  (fn [type ords]
    type))

(defmethod stored-ords->shape :polygon
  [type ords]
  (poly/polygon [(apply r/ords->ring (map stored->ordinate ords))]))

(def shape-type->integer
  "Converts a shape type into an integer for storage"
  {:polygon 1})

(def integer->shape-type
  "Map of shape type integers to the equivalent keyword type"
  (set/map-invert shape-type->integer))

(defn shapes->ords-info-map
  "Converts a sequence of shapes into a map contains the ordinate values and ordinate info"
  [shapes]
  (let [;; Convert each shape into a map of types and ordinates
        infos (map shape->stored-ords shapes)]

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
            shape-ords (take size ords)
            shape (stored-ords->shape type shape-ords)]
        (recur (rest ords-info-pairs)
               (drop size ords)
               (conj shapes shape))))))


