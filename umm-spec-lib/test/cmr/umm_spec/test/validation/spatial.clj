(ns cmr.umm-spec.test.validation.spatial
  "This has tests for UMM validations."
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.services.errors :as e]
   [cmr.common.util :as util :refer [are3]]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.umm-spec.test.validation.umm-spec-validation-test-helpers :as helpers]
   [cmr.umm-spec.validation.umm-spec-validation-core :as v]))

(defn- umm-spec-point
  "Returns a point for a umm-spec model."
  [lon lat]
  {:Longitude lon :Latitude lat})

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
  (let [valid-point {:Longitude 0 :Latitude 90} ;; north pole
        valid-mbr (umm-spec-mbr 0 0 0 0)
        invalid-point (umm-spec-point -181 0)
        invalid-mbr (umm-spec-mbr -180 45 180 46)]
    (testing "Valid spatial areas"
      (helpers/assert-valid (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [valid-point]}
                                                "GEODETIC"))
      (helpers/assert-valid (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [valid-point] :BoundingRectangles [valid-mbr]}
                                                "GEODETIC")))
    (testing "Invalid single geometry"
      (helpers/assert-invalid
        (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [invalid-point]}
                            "GEODETIC")
        [:SpatialExtent :HorizontalSpatialDomain :Geometry :Points 0]
        ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]))
    (testing "Invalid multiple geometry"
      (let [expected-errors [{:path [:SpatialExtent :HorizontalSpatialDomain :Geometry :Points 1]
                              :errors ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]}
                             {:path [:SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles 0]
                              :errors ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]}]]
        (helpers/assert-multiple-invalid (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [valid-point invalid-point] :BoundingRectangles [invalid-mbr]}
                                                             "GEODETIC")
                                     expected-errors)))))

(deftest collection-orbit-parameters
 (testing "Missing orbit params"
  (helpers/assert-invalid
   (coll/map->UMM-C {:SpatialExtent {:GranuleSpatialRepresentation "ORBIT"}})
   [:SpatialExtent]
   [(str "Orbit Parameters must be defined for a collection "
         "whose granule spatial representation is ORBIT.")])))

(deftest coordinate-systems
  (testing "Valid coordinate systems"
    (doseq [coordinate-system ["GEODETIC" "CARTESIAN"]]
      (helpers/assert-valid (coll-with-geometry {:CoordinateSystem coordinate-system} "NO_SPATIAL"))))
  (testing "Invalid coordinate system"
    (helpers/assert-invalid
     (coll-with-geometry {:CoordinateSystem "INVALID_COORDINATE_SYSTEM"} "NO_SPATIAL")
     [:SpatialExtent :HorizontalSpatialDomain :Geometry :CoordinateSystem]
     ["Value [INVALID_COORDINATE_SYSTEM] not found in enum (possible values: [\"CARTESIAN\",\"GEODETIC\"])"])))

(deftest granule-spatial-representations
  (testing "Geometry exists and granule spatial representation is nil"
    (helpers/assert-invalid
     (coll-with-geometry {:HorizontalSpatialDomain {:Geometry {:CoordinateSystem "GEODETIC"}}} nil)
     [:SpatialExtent]
     ["Granule Spatial Representation must be supplied."]))
  (testing "Not provided geometry and nil granule spatial representation"
    (helpers/assert-valid (coll/map->UMM-C {:SpatialExtent {:GranuleSpatialRepresentation nil}})))
  (testing "Valid granule spatial representations"
    (doseq [granule-spatial-representation ["GEODETIC" "CARTESIAN" "ORBIT" "NO_SPATIAL"]]
      (helpers/assert-valid (coll/map->UMM-C {:SpatialExtent
                                              {:GranuleSpatialRepresentation granule-spatial-representation
                                               :OrbitParameters {}
                                               :HorizontalSpatialDomain {:Geometry {:CoordinateSystem "GEODETIC"}}}}))))
  (testing "Invalid granule spatial representation"
    (helpers/assert-invalid
     (coll-with-geometry {:CoordinateSystem "CARTESIAN"} "INVALID_GRANULE_SPATIAL_REPRESENTATION")
     [:SpatialExtent]
     ["Value [INVALID_GRANULE_SPATIAL_REPRESENTATION] not found in enum (possible values: [\"CARTESIAN\",\"GEODETIC\",\"NO_SPATIAL\",\"ORBIT\"])"])))
