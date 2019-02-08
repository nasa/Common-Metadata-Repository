(ns cmr.spatial.line-string
  "This contains generic functions for operating on geodetic or cartesian line strings. Geodetic line
  strings are represented by the Arc record. Cartesian line strings are represented by the LineSegment
  record."
  (:require
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.common.util :as u]
   [cmr.spatial.arc :as a]
   [cmr.spatial.arc-line-segment-intersections :as asi]
   [cmr.spatial.derived :as d]
   [cmr.spatial.line-segment :as s]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.messages :as msg]
   [cmr.spatial.point :as p]
   [cmr.spatial.points-validation-helpers :as pv]
   [cmr.spatial.validation :as v]
   [primitive-math])
  (:import
   (cmr.spatial.arc Arc)
   (cmr.spatial.line_segment LineSegment)
   (cmr.spatial.mbr Mbr)))

(primitive-math/use-primitive-operators)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Segment helpers
;; These are sets of protocol methods on line segments and arcs

(defprotocol SegmentHelpers
  (segment->mbrs [segment] "Returns a sequence of mbrs covering the segment")
  (point-on-segment? [segment point] "Returns true if the segment covers the point"))

(extend-protocol SegmentHelpers
  Arc
  (segment->mbrs
    [arc]
    (a/mbrs arc))
  (point-on-segment?
    [arc point]
    (a/point-on-arc? arc point))

  LineSegment
  (segment->mbrs
    [ls]
    [(:mbr ls)])
  (point-on-segment?
    [ls point]
    (s/point-on-segment? ls point)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defrecord LineString
  [
   coordinate-system

   points

   ;; A set of the unique points in the line string.
   ;; This should be used as opposed to creating a set from the points many times over which is expensive.
   point-set

   ;; Derived fields
   ;; arcs or line-segments
   segments

   mbr])

(record-pretty-printer/enable-record-pretty-printing LineString)

(defn line-string
  ([points]
   (line-string nil points))
  ([coordinate-system points]
   (->LineString coordinate-system (mapv #(p/with-equality coordinate-system %) points) nil nil nil)))

(defn set-coordinate-system
  "Sets the coordinate system of the line string. You must recalculate derived data after setting this."
  [line coordinate-system]
  (-> line
      (assoc :coordinate-system coordinate-system)
      (update :points #(mapv (partial p/with-equality coordinate-system) %))
      ;; Set calculated data to nil.
      (assoc :point-set nil :segments nil :mbr nil)))

(defn ords->line-string
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates a line."
  [coordinate-system ords]
  (line-string coordinate-system (p/ords->points ords)))

(defn line-string->ords [line]
  (p/points->ords (:points line)))

(defmulti line-string->segments
  "Creates the segments representing the connections between each point"
  (fn [^LineString line]
    (.coordinate_system line)))

(defmethod line-string->segments :geodetic
  [^LineString line]
  (or (.segments line)
      (a/points->arcs (.points line))))

(defmethod line-string->segments :cartesian
  [^LineString line]
  (or (.segments line)
      (s/points->line-segments (.points line))))

(defn union-arc-segment-mbrs
  "Finds the minimum bounding rectangle of all the arcs and unions them together. This was written
  to be a performance optimized way to do it."
  [arcs]
  (reduce (fn [mbr ^Arc arc]
            (let [mbr (if mbr
                        (m/union mbr (.mbr1 arc))
                        (.mbr1 arc))]
              (if-let [mbr2 (.mbr2 arc)]
                (m/union mbr mbr2)
                mbr)))
          nil
          arcs))

(defn union-line-segment-mbrs
  "Finds the minimum bounding rectangle of all the line segments and unions them together."
  [line-segments]
  (reduce (fn [mbr ^LineSegment ls]
            (if mbr
              (m/union mbr (.mbr ls))
              (.mbr ls)))
          nil
          line-segments))

(defn line-string->mbr
  "Determines the mbr from the points in the line."
  [^LineString line]
  (or (.mbr line)
      (let [segments (line-string->segments line)]
        (if (= :geodetic (.coordinate_system line))
          (union-arc-segment-mbrs segments)
          (union-line-segment-mbrs segments)))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.line_string.LineString
  (calculate-derived
    [^LineString line]
    (if (.segments line)
      line
      (as-> line line
            (assoc line :point-set (set (.points line)))
            (assoc line :segments (line-string->segments line))
            (assoc line :mbr (line-string->mbr line))))))

(defn covers-point?
  "Returns true if the line covers the point"
  [^LineString line point]
  (let [point-set (.point_set line)
        segments (.segments line)]
    (or (contains? point-set point)
        (u/any-true? #(point-on-segment? % point) segments))))

(defn intersects-br?
  "Returns true if the line intersects the br"
  [^LineString line ^Mbr br]
  (when (m/intersects-br? (.coordinate_system line) (.mbr line) br)
    (if (m/single-point? br)
      (covers-point? line (p/point (.west br) (.north br)))

      (let [coord-sys (.coordinate_system line)]
        (or
         ;; Does the br cover any points of the line?
         (u/any-true? #(m/covers-point? coord-sys br %) (.points line))
         ;; Does the line contain any points of the br?
         (u/any-true? #(covers-point? line %) (m/corner-points br))
         ;; Do any of the sides intersect?
         (let [segments (.segments line)
               mbr-segments (s/mbr->line-segments br)]
           (loop [segments segments]
             (when-let [segment (first segments)]
               (let [intersects? (loop [mbr-segments mbr-segments]
                                   (if-let [mbr-segment (first mbr-segments)]
                                     (or (seq (asi/intersections segment mbr-segment))
                                         (recur (rest mbr-segments)))))]
                 (or intersects? (recur (rest segments))))))))))))

(defn intersects-line-string?
  "Returns true if the line string instersects the other line string"
  [line1 line2]
  (u/any-true? (fn [[s1 s2]]
                (seq (asi/intersections s1 s2)))
          (for [segment1 (:segments line1)
                segment2 (:segments line2)]
            [segment1 segment2])))

(extend-protocol v/SpatialValidation
  cmr.spatial.line_string.LineString
  (validate
    [line]
    ;; Certain validations can only be run if earlier validations passed. Validations are grouped
    ;; here so that subsequent validations won't run if earlier validations fail.
    (if (= (:coordinate-system line) :geodetic)
      (or (seq (pv/points-in-shape-validation line))
          (seq (concat (pv/duplicate-point-validation line)
                       (pv/consecutive-antipodal-points-validation line))))
      (or (seq (pv/points-in-shape-validation line))
          (seq (pv/duplicate-point-validation line))))))
