(ns cmr.umm.test.validation.granule
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm.validation.core :as v]
            [cmr.umm.collection :as c]
            [cmr.umm.granule :as g]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.common.date-time-parser :as dtp]
            [cmr.common.services.errors :as e]))

(defn assert-valid-gran
  "Asserts that the given granule is valid."
  [collection granule]
  (is (empty? (v/validate-granule collection granule))))

(defn assert-invalid-gran
  "Asserts that the given umm model is invalid and has the expected error messages.
  field-path is the path within the metadata to the error. expected-errors is a list of string error
  messages."
  [collection granule field-path expected-errors]
  (is (= [(e/map->PathErrors {:path field-path
                              :errors expected-errors})]
         (v/validate-granule collection granule))))

(defn assert-multiple-invalid-gran
  "Asserts there are multiple errors at different paths invalid with the UMM. Expected errors
  should be a list of maps with path and errors."
  [collection granule expected-errors]
  (is (= (set (map e/map->PathErrors expected-errors))
         (set (v/validate-granule collection granule)))))

(defn gran-with-geometries
  [geometries]
  (g/map->UmmGranule {:spatial-coverage (c/map->SpatialCoverage {:geometries geometries})}))

;; This is built on top of the existing spatial validation. It just ensures that the spatial
;; validation is being called
(deftest granule-spatial-coverage
  (let [collection (c/map->UmmCollection {:spatial-coverage {:granule-spatial-representation :geodetic}})
        valid-point p/north-pole
        valid-mbr (m/mbr 0 0 0 0)
        invalid-point (p/point -181 0)
        invalid-mbr (m/mbr -180 45 180 46)]
    (testing "Valid spatial areas"
      (assert-valid-gran collection (gran-with-geometries [valid-point]))
      (assert-valid-gran collection (gran-with-geometries [valid-point valid-mbr])))
    (testing "Invalid single geometry"
      (assert-invalid-gran
        collection
        (gran-with-geometries [invalid-point])
        [:spatial-coverage :geometries 0]
        ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]))
    (testing "Invalid multiple geometry"
      (let [expected-errors [{:path [:spatial-coverage :geometries 1]
                              :errors ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]}
                             {:path [:spatial-coverage :geometries 2]
                              :errors ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]}]]
        (assert-multiple-invalid-gran
          collection
          (gran-with-geometries [valid-point invalid-point invalid-mbr])
          expected-errors)))))

(deftest granule-project-refs
  (let [c1 (c/map->Project {:short-name "C1"})
        c2 (c/map->Project {:short-name "C2"})
        c3 (c/map->Project {:short-name "C3"})
        collection (c/map->UmmCollection {:projects [c1 c2 c3]})]
    (testing "Valid project-refs"
      (assert-valid-gran collection (g/map->UmmGranule {}))
      (assert-valid-gran collection (g/map->UmmGranule {:project-refs ["C1"]}))
      (assert-valid-gran collection (g/map->UmmGranule {:project-refs ["C1" "C2" "C3"]})))
    (testing "Invalid project-refs"
      (assert-invalid-gran
        collection
        (g/map->UmmGranule {:project-refs ["C4"]})
        [:projects]
        ["Projects has [C4] which do not reference any projects in parent collection."])
      (assert-invalid-gran
        collection
        (g/map->UmmGranule {:project-refs ["C1" "C2" "C3" "C4" "C5"]})
        [:projects]
        ["Projects has [C5, C4] which do not reference any projects in parent collection."]))))

(deftest granule-platform-refs
  (let [p1 (c/map->Platform {:short-name "p1"})
        p2 (c/map->Platform {:short-name "p2"})
        p3 (c/map->Platform {:short-name "p3"})
        p4 (c/map->Platform {:short-name "P3"})
        pg1 (g/map->PlatformRef {:short-name "p1" :instrument-refs {}})
        pg2 (g/map->PlatformRef {:short-name "p2" :instrument-refs {}})
        pg3 (g/map->PlatformRef {:short-name "p3" :instrument-refs {}})
        pg4 (g/map->PlatformRef {:short-name "p4" :instrument-refs {}})
        pg5 (g/map->PlatformRef {:short-name "p5" :instrument-refs {}})
        pg6 (g/map->PlatformRef {:short-name "P3" :instrument-refs {}})
        collection (c/map->UmmCollection {:platforms [p1 p2 p3 p4]})]
    (testing "Valid platform-refs"
      (assert-valid-gran collection (g/map->UmmGranule {}))
      (assert-valid-gran collection (g/map->UmmGranule {:platform-refs [pg1]}))
      (assert-valid-gran collection (g/map->UmmGranule {:platform-refs [pg1 pg2 pg3]})))
      (assert-valid-gran collection (g/map->UmmGranule {:platform-refs [pg3 pg6]}))
    (testing "Invalid platform-refs"
      (assert-invalid-gran
        collection
        (g/map->UmmGranule {:platform-refs [pg4]})
        [:platform-refs]
        ["Platform Refs referenced in a granule must be present in the parent collection. The invalid short-names are [p4]."])
      (assert-invalid-gran
        collection
        (g/map->UmmGranule {:platform-refs [pg1 pg2 pg3 pg4 pg5]})
        [:platform-refs]
        ["Platform Refs referenced in a granule must be present in the parent collection. The invalid short-names are [p4, p5]."])
      (assert-invalid-gran
        collection
        (g/map->UmmGranule {:platform-refs [pg1 pg1 pg2]})
        [:platform-refs]
        ["Platform Refs must be unique. This contains duplicates named [p1]."])
      (assert-invalid-gran
        collection
        (g/map->UmmGranule {:platform-refs [pg1 pg1 pg2 pg2]})
        [:platform-refs]
        ["Platform Refs must be unique. This contains duplicates named [p1, p2]."])
      (assert-invalid-gran
        collection
        (g/map->UmmGranule {:platform-refs [pg1 pg1 pg3 pg4 pg5]})
        [:platform-refs]
        ["Platform Refs must be unique. This contains duplicates named [p1]."
         "Platform Refs referenced in a granule must be present in the parent collection. The invalid short-names are [p4, p5]."]))))