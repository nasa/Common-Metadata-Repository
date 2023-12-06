(ns cmr.umm-spec.test.validation.spatial
  "This has tests for UMM validations."
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as helpers]))

(defn- umm-spec-mbr
  "Returns an mbr for a umm-spec model."
  [west north east south]
  {:WestBoundingCoordinate west
   :NorthBoundingCoordinate north
   :EastBoundingCoordinate east
   :SouthBoundingCoordinate south})

(defn- coll-with-geometry
  "Returns a umm-spec collection model with the given geometry."
  [geometry gsr]
  (coll/map->UMM-C {:SpatialExtent {:HorizontalSpatialDomain {:Geometry geometry}
                                    :GranuleSpatialRepresentation gsr}}))

;; This is built on top of the existing spatial validation. It just ensures that the spatial
;; validation is being called.
(deftest collection-spatial-coverage
  (let [invalid-mbr (umm-spec-mbr -180 45 180 46)]
    (testing "Invalid multiple geometry"
      (let [expected-errors [{:path [:SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles 0]
                              :errors ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]}]]
        (helpers/assert-multiple-invalid (coll-with-geometry {:CoordinateSystem "GEODETIC" :BoundingRectangles [invalid-mbr]}
                                                             "GEODETIC")
                                         expected-errors)))))
