(ns cmr.spatial.polygon
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]))

(primitive-math/use-primitive-operators)

(defrecord Polygon
  [
   rings

   ;; Derived fields
   mbr
   ])

(defn polygon
  "Creates a polygon"
  [rings]
  (->Polygon rings nil))

(extend-protocol d/DerivedCalculator
  cmr.spatial.polygon.Polygon
  (calculate-derived
    [^Polygon polygon]
    (if (.mbr polygon)
      polygon

      (as-> polygon p
            (update-in p [:rings] (partial map d/calculate-derived))
            (assoc p :mbr (-> p :rings first :mbr))))))