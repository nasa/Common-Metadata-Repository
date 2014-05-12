(ns cmr.spatial.polygon
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]))

(primitive-math/use-primitive-operators)

(defrecord Polygon
  [
   rings
   ])

(defn polygon
  "Creates a polygon"
  [rings]
  (->Polygon rings))