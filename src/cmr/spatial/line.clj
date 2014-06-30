(ns cmr.spatial.line
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(defrecord Line
  [
   points

  ;; Derived fields
   arcs

   mbr
   ])

(defn line
  [points]
  (->Line points nil nil))

(defn line->arcs
  "Determines the arcs from the points in the line."
  [^Line line]
  (or (.arcs line)
      (a/points->arcs (.points line))))

(defn line->mbr
  "Determines the mbr from the points in the line."
  [^Line line]
  (or (.mbr line)
      (let [arcs (line->arcs line)]
        (->> arcs (mapcat a/mbrs) (reduce mbr/union)))))

(extend-protocol d/DerivedCalculator
  cmr.spatial.line.Line
  (calculate-derived
    [^Line line]
    (if (.arcs line)
      line

      (as-> line line
            (assoc line :arcs (line->arcs line))
            (assoc line :mbr (line->mbr line))))))

(defn ords->line
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates a line."
  [& ords]
  (line (apply p/ords->points ords)))

(defn line->ords [line]
  (p/points->ords (:points line)))