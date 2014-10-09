(ns cmr.search.services.aql.converters.spatial
  "Handles converting AQL spatial conditions into spatial search conditions"
  (:require [clojure.data.xml :as x]
            [cmr.search.services.aql.conversion :as c]
            [cmr.common.xml :as cx]
            [cmr.spatial.point :as p]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.line-string :as ls]
            [cmr.search.models.query :as qm]))

(defmulti spatial-element->shape
  "Converts a aql spatial area element into a spatial shape"
  (fn [elem]
    (:tag elem)))

(defmethod spatial-element->shape :IIMSPoint
  [element]
  (let [{{^String lat :lat ^String lon :long} :attrs} element]
    (p/point (Double. lon) (Double. lat))))

(defmethod spatial-element->shape :IIMSPolygon
  [element]
  (let [points (map spatial-element->shape
                    (cx/elements-at-path element [:IIMSLRing :IIMSPoint]))]
    (poly/polygon :geodetic [(rr/ring :geodetic points)])))

(defmethod spatial-element->shape :IIMSBox
  [element]
  (let [[lower-left
         upper-right] (map spatial-element->shape
                           (cx/elements-at-path element [:IIMSPoint]))]
    (m/mbr (:lon lower-left) (:lat upper-right) (:lon upper-right) (:lat lower-left))))

(defmethod spatial-element->shape :IIMSLine
  [element]
  (ls/line-string
    :geodetic (map spatial-element->shape
                   (cx/elements-at-path element [:IIMSPoint]))))

(defmethod c/element->condition :spatial
  [concept-type element]
  ;; TOOD TwoDCoordinateSystem could be a second child of spatial
  (qm/->SpatialCondition (spatial-element->shape (first (:content element)))))
