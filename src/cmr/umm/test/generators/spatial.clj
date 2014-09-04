(ns cmr.umm.test.generators.spatial
  (:require [cmr.spatial.test.generators :as sgen]
            [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.line-string :as l]
            [cmr.umm.granule :as g]
            [cmr.umm.spatial :as umm-s]
            [cmr.umm.test.generators.granule.orbit-calculated-spatial-domain :as ocsd]))

(def generic-rings
  "Generates rings that are not valid but could be used for testing where validity is not important"
  (gen/fmap
    (fn [points]
      (umm-s/ring (concat points [(first points)])))
    (gen/bind (gen/choose 3 5) sgen/non-antipodal-points)))

(def polygons
  "Generates polygons that are not valid but could be used for testing where validity is not important"
  (gen/fmap poly/polygon
            (gen/vector generic-rings 1 4)))

(def lines
  (gen/fmap l/line-string
            (gen/bind (gen/choose 2 6) sgen/non-antipodal-points)))

(def geometries
  "A generator returning individual points, bounding rectangles, lines, and polygons.
  The spatial areas generated will not necessarily be valid."
  (gen/one-of [sgen/points sgen/mbrs lines polygons]))

(def orbit-directions
  (gen/elements [:asc :desc]))

(def orbits
  "A generator returning an Orbit record for a spatial domain."
  (ext-gen/model-gen
    g/->Orbit
    ocsd/longitude
    ocsd/latitude
    orbit-directions
    ocsd/latitude
    orbit-directions
    (optional (gen/tuple ocsd/latitude ocsd/longitude))))