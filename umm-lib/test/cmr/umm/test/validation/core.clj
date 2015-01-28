(ns cmr.umm.test.validation.core
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm.validation.core :as v]
            [cmr.umm.collection :as c]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.common.date-time-parser :as dtp]))


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
                               :sensors [s1 s2]})
        c1 (c/map->Characteristic {:name "C1"})
        c2 (c/map->Characteristic {:name "C2"})]
    (testing "valid platforms"
      (assert-valid (c/map->UmmCollection
                      {:platforms [(c/map->Platform {:short-name "P1"
                                                     :instruments [i1 i2]
                                                     :characteristics [c1 c2]})
                                   (c/map->Platform {:short-name "P2"
                                                     :instruments [i1 i2]
                                                     :characteristics [c1 c2]})]})))

    (testing "invalid platforms"
      (testing "duplicate platform short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"})
                                  (c/map->Platform {:short-name "P1"})]})]
          (assert-invalid
            coll :echo10
            ["Platforms must be unique. This contains duplicates named [P1]."])))
      (testing "duplicate platform characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"
                                                    :characteristics [c1 c1]})]})]
          (assert-invalid
            coll :echo10
            ["Platform characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate instrument short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"
                                                    :instruments [i1 i1]})]})]
          (assert-invalid
            coll :echo10
            ["Instruments must be unique. This contains duplicates named [I1]."])))
      (testing "duplicate instrument characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument
                                                     {:short-name "I1"
                                                      :characteristics [c1 c1]})]})]})]
          (assert-invalid
            coll :echo10
            ["Instrument characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate sensor short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s1]})]})]})]
          (assert-invalid
            coll :echo10
            ["Sensors must be unique. This contains duplicates named [S1]."])))
      (testing "duplicate sensor characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument
                                                     {:short-name "I1"
                                                      :sensors [(c/map->Sensor
                                                                  {:short-name "S1"
                                                                   :characteristics [c2 c2]})]})]})]})]
          (assert-invalid
            coll :echo10
            ["Sensor characteristics must be unique. This contains duplicates named [C2]."])))
      (testing "multiple errors"
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
             "Sensors must be unique. This contains duplicates named [S2]."
             "Instruments must be unique. This contains duplicates named [I1]."
             "Platforms must be unique. This contains duplicates named [P1]."]))))))

(deftest collection-associated-difs-validation
  (testing "valid associated difs"
    (assert-valid (c/map->UmmCollection {:associated-difs ["d1" "d2" "d3"]})))

  (testing "invalid associated difs"
    (testing "duplicate names"
      (let [coll (c/map->UmmCollection {:associated-difs ["d1" "d2" "d1"]})]
        (assert-invalid
          coll :echo10
          ["AssociatedDIFs must be unique. This contains duplicates named [d1]."])))))

(defn- range-date-time
  [begin-date-time end-date-time]
  (let [begin-date-time (when begin-date-time (dtp/parse-datetime begin-date-time))
        end-date-time (when end-date-time (dtp/parse-datetime end-date-time))]
    (c/map->RangeDateTime
      {:beginning-date-time begin-date-time
       :ending-date-time end-date-time})))

(defn coll-with-range-date-times
  [range-date-times]
  (c/map->UmmCollection
    {:temporal (c/map->Temporal {:range-date-times range-date-times})}))

(deftest collection-temporal-validation
  (testing "valid temporal"
    (let [r1 (range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:01Z")
          r2 (range-date-time "1999-12-30T19:00:00Z" nil)]
      (assert-valid (coll-with-range-date-times [r1]))
      (assert-valid (coll-with-range-date-times [r2]))
      (assert-valid (coll-with-range-date-times [r1 r2]))))

  (testing "invalid temporal"
    (testing "single error"
      (let [r1 (range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            coll (coll-with-range-date-times [r1])]
        (assert-invalid
          coll :echo10
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])))

    (testing "multiple errors"
      (let [r1 (range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (range-date-time "2000-12-30T19:00:02Z" "2000-12-30T19:00:01Z")
            coll (coll-with-range-date-times [r1 r2])]
        (assert-invalid
          coll :echo10
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"
           "BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"])))))

(deftest collection-online-access-urls-validation
  (let [url "http://example.com/url2"
        r1 (c/map->RelatedURL {:type "GET DATA"
                               :url "http://example.com/url1"})
        r2 (c/map->RelatedURL {:type "GET DATA"
                               :url url})
        r3 (c/map->RelatedURL {:type "GET RELATED VISUALIZATION"
                               :url url})]
    (testing "valid online access urls"
      (assert-valid (c/map->UmmCollection {:related-urls [r1 r2 r3]})))

    (testing "invalid online access urls"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:related-urls [r1 r2 r2]})]
          (assert-invalid
            coll :echo10
            [(format "OnlineAccessURLs must be unique. This contains duplicates named [%s]." url)]))))))

(deftest collection-two-d-coordinate-systems-validation
  (let [t1 (c/map->TwoDCoordinateSystem {:name "T1"})
        t2 (c/map->TwoDCoordinateSystem {:name "T2"})]
    (testing "valid two-d-coordinate-systems"
      (assert-valid (c/map->UmmCollection {:two-d-coordinate-systems [t1 t2]})))

    (testing "invalid two-d-coordinate-systems"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:two-d-coordinate-systems [t1 t1]})]
          (assert-invalid
            coll :echo10
            ["TwoDCoordinateSystems must be unique. This contains duplicates named [T1]."]))))))

