(ns cmr.umm-spec.test.validation.spatial
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.validation.core :as v]
            [cmr.umm-spec.models.collection :as coll]
            [cmr.umm-spec.test.validation.helpers :as helpers]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.common.services.errors :as e]))

(defn assert-valid
  "Asserts that the given collection is valid."
  [collection]
  (is (empty? (v/validate-collection collection))))

(defn assert-invalid
  "Asserts that the given umm model is invalid and has the expected error messages.
  field-path is the path within the metadata to the error. expected-errors is a list of string error
  messages."
  [collection field-path expected-errors]
  (is (= [(e/map->PathErrors {:path field-path
                              :errors expected-errors})]
         (v/validate-collection collection))))

(defn assert-multiple-invalid
  "Asserts there are multiple errors at different paths invalid with the UMM. Expected errors
  should be a list of maps with path and errors."
  [collection expected-errors]
  (is (= (set (map e/map->PathErrors expected-errors))
         (set (v/validate-collection collection)))))

(defn- umm-spec-point
  "Returns a point for a umm-spec model."
  [lon lat]
  {:Longitude lon :Latitude lat})

(defn- umm-lib-point->umm-spec-point
  "Converts a umm-lib point to a umm-spec point."
  [{:keys [lon lat] :as point}]
  (umm-spec-point lon lat))

(defn- umm-spec-mbr
  "Returns an mbr for a umm-spec model."
  [west north east south]
  {:WestBoundingCoordinate west
   :NorthBoundingCoordinate north
   :EastBoundingCoordinate east
   :SouthBoundingCoordinate south})

(defn coll-with-geometry
  "Returns a umm-spec collection model with the given geometry."
  [geometry]
  (coll/map->UMM-C {:SpatialExtent {:HorizontalSpatialDomain {:Geometry geometry}}}))

;; This is built on top of the existing spatial validation. It just ensures that the spatial
;; validation is being called.
(deftest collection-spatial-coverage
  (let [valid-point (umm-lib-point->umm-spec-point p/north-pole)
        valid-mbr (umm-spec-mbr 0 0 0 0)
        invalid-point (umm-spec-point -181 0)
        invalid-mbr (umm-spec-mbr -180 45 180 46)]
    (testing "Valid spatial areas"
      (assert-valid (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [valid-point]}))
      (assert-valid (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [valid-point] :BoundingRectangles [valid-mbr]})))
    (testing "Invalid single geometry"
      (assert-invalid
        (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [invalid-point]})
        [:SpatialExtent :HorizontalSpatialDomain :Geometry :Points 0]
        ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]))
    (testing "Invalid multiple geometry"
      (let [expected-errors [{:path [:SpatialExtent :HorizontalSpatialDomain :Geometry :Points 1]
                              :errors ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]}
                             {:path [:SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles 0]
                              :errors ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]}]]
        (assert-multiple-invalid (coll-with-geometry {:CoordinateSystem "GEODETIC" :Points [valid-point invalid-point] :BoundingRectangles [invalid-mbr]})
                                 expected-errors)))))
