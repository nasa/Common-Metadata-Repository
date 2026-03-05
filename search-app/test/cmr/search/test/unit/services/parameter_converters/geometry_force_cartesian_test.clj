(ns cmr.search.test.unit.services.parameter-converters.geometry-force-cartesian-test
  "Tests for force-cartesian parameter in geometry converters"
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.search.services.parameters.converters.geometry :as geo]
   [cmr.spatial.ring-relations :as rr])
  (:import
   (org.locationtech.jts.geom Coordinate GeometryFactory LinearRing LineString Polygon)))

(def ^GeometryFactory geometry-factory
  "JTS GeometryFactory for creating test geometries"
  (GeometryFactory.))

(defn make-line-string
  "Create a JTS LineString from coordinate pairs"
  [& coords]
  (let [coord-array (into-array Coordinate
                                 (map (fn [[lon lat]] (Coordinate. lon lat)) (partition 2 coords)))]
    (.createLineString geometry-factory coord-array)))

(defn make-linear-ring
  "Create a JTS LinearRing from coordinate pairs"
  [& coords]
  (let [coord-array (into-array Coordinate
                                 (map (fn [[lon lat]] (Coordinate. lon lat)) (partition 2 coords)))]
    (.createLinearRing geometry-factory coord-array)))

(defn make-polygon
  "Create a JTS Polygon from exterior ring coordinates and optional hole coordinates"
  [exterior-coords & hole-coords]
  (let [exterior-ring (apply make-linear-ring exterior-coords)
        hole-rings (map #(apply make-linear-ring %) hole-coords)
        hole-array (into-array LinearRing hole-rings)]
    (.createPolygon geometry-factory exterior-ring hole-array)))

(deftest line-string-ring->ring-force-cartesian-test
  (testing "line-string-ring->ring with force-cartesian option"
    (let [line-string (make-line-string 0 0, 10 0, 10 10, 0 10, 0 0)]

      (testing "force-cartesian true creates cartesian ring"
        (let [ring (geo/line-string-ring->ring line-string {:force-cartesian true})]
          (is (= :cartesian (rr/coordinate-system ring))
              "Ring should have cartesian coordinate system")))

      (testing "force-cartesian false creates geodetic ring"
        (let [ring (geo/line-string-ring->ring line-string {:force-cartesian false})]
          (is (= :geodetic (rr/coordinate-system ring))
              "Ring should have geodetic coordinate system")))

      (testing "no force-cartesian option defaults to geodetic"
        (let [ring (geo/line-string-ring->ring line-string {})]
          (is (= :geodetic (rr/coordinate-system ring))
              "Ring should default to geodetic coordinate system"))
        (let [ring (geo/line-string-ring->ring line-string)]
          (is (= :geodetic (rr/coordinate-system ring))
              "Ring should default to geodetic coordinate system with no options"))))))

(deftest polygon->shape-force-cartesian-test
  (testing "polygon->shape with force-cartesian option"
    (let [exterior-coords [0 0, 10 0, 10 10, 0 10, 0 0]
          polygon (make-polygon exterior-coords)]

      (testing "force-cartesian true creates cartesian polygon"
        (let [shape (geo/polygon->shape polygon {:force-cartesian true})]
          (is (= :cartesian (:coordinate-system shape))
              "Polygon should have cartesian coordinate system")
          ;; Verify all rings have cartesian coordinate system
          (doseq [ring (:rings shape)]
            (is (= :cartesian (rr/coordinate-system ring))
                "All rings should have cartesian coordinate system"))))

      (testing "force-cartesian false creates geodetic polygon"
        (let [shape (geo/polygon->shape polygon {:force-cartesian false})]
          (is (= :geodetic (:coordinate-system shape))
              "Polygon should have geodetic coordinate system")
          (doseq [ring (:rings shape)]
            (is (= :geodetic (rr/coordinate-system ring))
                "All rings should have geodetic coordinate system"))))

      (testing "no force-cartesian option defaults to geodetic"
        (let [shape (geo/polygon->shape polygon {})]
          (is (= :geodetic (:coordinate-system shape))
              "Polygon should default to geodetic coordinate system")
          (doseq [ring (:rings shape)]
            (is (= :geodetic (rr/coordinate-system ring))
                "All rings should default to geodetic coordinate system")))))))

(deftest polygon-with-holes-force-cartesian-test
  (testing "polygon->shape with holes and force-cartesian option"
    (let [exterior-coords [0 0, 20 0, 20 20, 0 20, 0 0]
          hole1-coords [5 5, 8 5, 8 8, 5 8, 5 5]
          hole2-coords [12 12, 15 12, 15 15, 12 15, 12 12]
          polygon (make-polygon exterior-coords hole1-coords hole2-coords)]

      (testing "force-cartesian true creates cartesian polygon with cartesian holes"
        (let [shape (geo/polygon->shape polygon {:force-cartesian true})]
          (is (= :cartesian (:coordinate-system shape))
              "Polygon should have cartesian coordinate system")
          (is (= 3 (count (:rings shape)))
              "Polygon should have 3 rings (1 exterior + 2 holes)")
          ;; Verify all rings (exterior and holes) have cartesian coordinate system
          (doseq [ring (:rings shape)]
            (is (= :cartesian (rr/coordinate-system ring))
                "All rings including holes should have cartesian coordinate system"))))

      (testing "no force-cartesian option defaults to geodetic for all rings"
        (let [shape (geo/polygon->shape polygon {})]
          (is (= :geodetic (:coordinate-system shape))
              "Polygon should default to geodetic coordinate system")
          (is (= 3 (count (:rings shape)))
              "Polygon should have 3 rings (1 exterior + 2 holes)")
          (doseq [ring (:rings shape)]
            (is (= :geodetic (rr/coordinate-system ring))
                "All rings including holes should default to geodetic coordinate system")))))))

(deftest line->shape-force-cartesian-test
  (testing "line->shape with force-cartesian option"
    (let [line-string (make-line-string -77 38.9, -77.4 37.54)]

      (testing "force-cartesian true creates cartesian line-string"
        (let [shape (geo/line->shape line-string {:force-cartesian true})]
          (is (= :cartesian (:coordinate-system shape))
              "Line-string should have cartesian coordinate system")))

      (testing "force-cartesian false creates geodetic line-string"
        (let [shape (geo/line->shape line-string {:force-cartesian false})]
          (is (= :geodetic (:coordinate-system shape))
              "Line-string should have geodetic coordinate system")))

      (testing "no force-cartesian option defaults to geodetic"
        (let [shape (geo/line->shape line-string {})]
          (is (= :geodetic (:coordinate-system shape))
              "Line-string should default to geodetic coordinate system"))
        (let [shape (geo/line->shape line-string)]
          (is (= :geodetic (:coordinate-system shape))
              "Line-string should default to geodetic coordinate system with no options"))))))
