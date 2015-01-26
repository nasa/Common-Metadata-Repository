(ns cmr.umm.test.validation.core
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm.validation.core :as v]
            [cmr.umm.collection :as c]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]))


(defn assert-valid
  "Asserts that the given umm model is valid."
  [umm]
  (is (empty? (v/validate :echo10 umm))))

(defn assert-invalid
  "Asserts that the given umm model is invalid and has the expected error messages."
  [umm metadata-format expected-errors]
  (is (= (set expected-errors)
         (set (v/validate metadata-format umm)))))

(defn coll-with-psas
  [psas]
  (c/map->UmmCollection {:product-specific-attributes psas}))

(defn coll-with-geometries
  [geometries]
  (c/map->UmmCollection {:spatial-coverage (c/map->SpatialCoverage {:geometries geometries})}))

;; This is built on top of the existing spatial validation. It just ensures that the spatial
;; validation is being called
(deftest collection-spatial-coverage
  (let [valid-point p/north-pole
        valid-mbr (m/mbr 0 0 0 0)
        invalid-point (p/point -181 0)
        invalid-mbr (m/mbr -180 45 180 46)]
    (testing "Valid spatial areas"
      (assert-valid (coll-with-geometries [valid-point]))
      (assert-valid (coll-with-geometries [valid-point valid-mbr])))
    (testing "Invalid other formats"
      (doseq [metadata-format [:dif :iso-smap :iso19115]]
        (assert-invalid
          (coll-with-geometries [invalid-point])
          metadata-format
          ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"])))
    (testing "Invalid single geometry"
      (assert-invalid
        (coll-with-geometries [invalid-point])
        :echo10
        ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]))
    (testing "Invalid multiple geometry"
      (assert-invalid
        (coll-with-geometries [valid-point invalid-point invalid-mbr])
        :echo10
        ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"
         "Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]))))

(deftest collection-product-specific-attributes-validation
  (testing "valid product specific attributes"
    (assert-valid (coll-with-psas [{:name "foo"} {:name "bar"}])))

  (testing "invalid product specific attributes"
    (testing "duplicate names"
      (let [coll (coll-with-psas [{:name "foo"} {:name "foo"} {:name "bar"} {:name "bar"}
                                  {:name "charlie"}])]
        (assert-invalid
          coll :echo10
          ["AdditionalAttributes must be unique. This contains duplicates named [foo, bar]."])
        (assert-invalid
          coll :dif
          ["AdditionalAttributes must be unique. This contains duplicates named [foo, bar]."])))))

(deftest collection-projects-validation
  (let [c1 (c/map->Project {:short-name "C1"})
        c2 (c/map->Project {:short-name "C2"})
        c3 (c/map->Project {:short-name "C3"})]
    (testing "valid projects"
      (assert-valid (c/map->UmmCollection {:projects [c1 c2]})))

    (testing "invalid projects"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:projects [c1 c1 c2 c2 c3]})]
          (assert-invalid
            coll :echo10
            ["Campaigns must be unique. This contains duplicates named [C1, C2]."])
          (assert-invalid
            coll :dif
            ["Project must be unique. This contains duplicates named [C1, C2]."])
          (assert-invalid
            coll :iso19115
            ["MI_Metadata/acquisitionInformation/MI_AcquisitionInformation/operation/MI_Operation must be unique. This contains duplicates named [C1, C2]."]))))))

(deftest collection-platforms-validation
  (let [s1 (c/map->Sensor {:short-name "S1"})
        s2 (c/map->Sensor {:short-name "S2"})
        i1 (c/map->Instrument {:short-name "I1"
                               :sensors [s1 s2]})
        i2 (c/map->Instrument {:short-name "I2"
                               :sensors [s1 s2]})]
    (testing "valid platforms"
      (assert-valid (c/map->UmmCollection
                      {:platforms [(c/map->Platform {:short-name "P1"
                                                     :instruments [i1 i2]})
                                   (c/map->Platform {:short-name "P2"
                                                     :instruments [i1 i2]})]})))

    (testing "invalid platforms"
      (testing "duplicate platform short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"})
                                  (c/map->Platform {:short-name "P1"})]})]
          (assert-invalid
            coll :echo10
            ["Platforms must be unique. This contains duplicates named [P1]."])))
      (testing "duplicate instrument short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"
                                                    :instruments [i1 i1]})]})]
          (assert-invalid
            coll :echo10
            ["Instruments must be unique. This contains duplicates named [I1]."])))
      (testing "duplicate sensor short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s1]})]})]})]
          (assert-invalid
            coll :echo10
            ["Sensors must be unique. This contains duplicates named [S1]."])))
      (testing "multiple errors"
        ;; Since we short circuit validations in cmr.common.validations.core seq-of-validations,
        ;; we only get the lowest level of validation errors.
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"})
                                  (c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s1]})
                                                   (c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s2 s2]})]})]})]
          (assert-invalid
            coll :echo10
            ["Sensors must be unique. This contains duplicates named [S1]."
             "Sensors must be unique. This contains duplicates named [S2]."]))))))

(deftest collection-associated-difs-validation
  (testing "valid associated difs"
    (assert-valid (c/map->UmmCollection {:associated-difs ["d1" "d2" "d3"]})))

  (testing "invalid projects"
    (testing "duplicate names"
      (let [coll (c/map->UmmCollection {:associated-difs ["d1" "d2" "d1"]})]
        (assert-invalid
          coll :echo10
          ["AssociatedDIFs must be unique. This contains duplicates named [d1]."])))))

