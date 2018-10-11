(ns cmr.umm.test.generators.spatial
  (:require [cmr.spatial.test.generators :as sgen]
            [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.line-string :as l]
            [cmr.umm.umm-granule :as g]
            [cmr.umm.umm-spatial :as umm-s]))


(def longitude
  (ext-gen/choose-double -180 180))

(def latitude
  (ext-gen/choose-double -90 90))

(def orbital-model-name
  (ext-gen/string-ascii 1 10))

(def orbit-number
  gen/int)

(def s-orbit-number
  "start/stop orbit number"
  gen/int)

(def orbit-calculated-spatial-domains
  (gen/fmap (fn [[omn on son spon ecl ecdt]]
              (g/->OrbitCalculatedSpatialDomain omn on son spon ecl ecdt))
            (gen/tuple orbital-model-name
                       orbit-number
                       s-orbit-number
                       s-orbit-number
                       longitude
                       ext-gen/date-time)))

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
    g/map->Orbit
    (gen/hash-map
      :ascending-crossing longitude
      :start-lat latitude
      :start-direction orbit-directions
      :end-lat latitude
      :end-direction orbit-directions)))
