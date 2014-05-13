(ns cmr.umm.test.generators.spatial
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.umm.granule :as g]
            [cmr.spatial.ring :as r]
            [cmr.spatial.polygon :as p]
            [cmr.spatial.test.generators :as sgen]))

(def rings
  (gen/fmap
    (fn [points]
      (r/ring (concat points [(first points)])))
    (gen/bind (gen/choose 3 10) sgen/non-antipodal-points)))

(def polygons
  (ext-gen/model-gen p/polygon (gen/vector rings 1 4)))

(def geometries
  "A generator returning individual points, bounding rectangles, lines, and polygons.
  The spatial areas generated will not necessarily be valid. This generator is designe for use
  testing XML generation etc."
  (gen/one-of [sgen/points sgen/mbrs sgen/lines polygons]))
