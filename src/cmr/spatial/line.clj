(ns cmr.spatial.line
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(defrecord Line
  [
   points

   ;;TODO add more as needed

   ])

(defn line
  [points]
  (->Line points))