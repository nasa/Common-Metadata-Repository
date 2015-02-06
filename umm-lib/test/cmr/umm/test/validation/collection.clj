(ns cmr.umm.test.validation.collection
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm.validation.core :as v]
            [cmr.umm.collection :as c]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.common.date-time-parser :as dtp]
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
    (testing "Invalid single geometry"
      (assert-invalid
        (coll-with-geometries [invalid-point])
        [:spatial-coverage :geometries 0]
        ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]))
    (testing "Invalid multiple geometry"
      (let [expected-errors [{:path [:spatial-coverage :geometries 1]
                              :errors ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]}
                             {:path [:spatial-coverage :geometries 2]
                              :errors ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]}]]
        (assert-multiple-invalid (coll-with-geometries [valid-point invalid-point invalid-mbr])
                                 expected-errors)))))

(deftest collection-product-specific-attributes-validation
  (testing "valid product specific attributes"
    (assert-valid (coll-with-psas [{:name "foo"} {:name "bar"}])))

  (testing "invalid product specific attributes"
    (testing "duplicate names"
      (let [coll (coll-with-psas [{:name "foo"} {:name "foo"} {:name "bar"} {:name "bar"}
                                  {:name "charlie"}])]
        (assert-invalid
          coll
          [:product-specific-attributes]
          ["Product Specific Attributes must be unique. This contains duplicates named [foo, bar]."])))))

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
            coll
            [:projects]
            ["Projects must be unique. This contains duplicates named [C1, C2]."]))))))


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
            coll
            [:platforms]
            ["Platforms must be unique. This contains duplicates named [P1]."])))
      (testing "duplicate platform characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"
                                                    :characteristics [c1 c1]})]})]
          (assert-invalid
            coll
            [:platforms 0 :characteristics]
            ["Characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate instrument short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"
                                                    :instruments [i1 i1]})]})]
          (assert-invalid
            coll
            [:platforms 0 :instruments]
            ["Instruments must be unique. This contains duplicates named [I1]."])))
      (testing "duplicate instrument characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument
                                                     {:short-name "I1"
                                                      :characteristics [c1 c1]})]})]})]
          (assert-invalid
            coll
            [:platforms 0 :instruments 0 :characteristics]
            ["Characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate sensor short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s1]})]})]})]
          (assert-invalid
            coll
            [:platforms 0 :instruments 0 :sensors]
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
            coll
            [:platforms 0 :instruments 0 :sensors 0 :characteristics]
            ["Characteristics must be unique. This contains duplicates named [C2]."])))
      (testing "multiple errors"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"})
                                  (c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s1]})
                                                   (c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s2 s2]})]})]})
              expected-errors [{:path [:platforms 1 :instruments 0 :sensors]
                                :errors ["Sensors must be unique. This contains duplicates named [S1]."]}
                               {:path [:platforms 1 :instruments 1 :sensors]
                                :errors ["Sensors must be unique. This contains duplicates named [S2]."]}
                               {:path [:platforms 1 :instruments]
                                :errors ["Instruments must be unique. This contains duplicates named [I1]."]}
                               {:path [:platforms]
                                :errors ["Platforms must be unique. This contains duplicates named [P1]."]}]]
          (assert-multiple-invalid coll expected-errors))))))

(deftest collection-associated-difs-validation
  (testing "valid associated difs"
    (assert-valid (c/map->UmmCollection {:associated-difs ["d1" "d2" "d3"]})))

  (testing "invalid associated difs"
    (testing "duplicate names"
      (let [coll (c/map->UmmCollection {:associated-difs ["d1" "d2" "d1"]})]
        (assert-invalid
          coll
          [:associated-difs]
          ["Associated Difs must be unique. This contains duplicates named [d1]."])))))

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
          r2 (range-date-time "1999-12-30T19:00:00Z" nil)
          r3 (range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
      (assert-valid (coll-with-range-date-times [r1]))
      (assert-valid (coll-with-range-date-times [r2]))
      (assert-valid (coll-with-range-date-times [r3]))
      (assert-valid (coll-with-range-date-times [r1 r2 r3]))))

  (testing "invalid temporal"
    (testing "single error"
      (let [r1 (range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            coll (coll-with-range-date-times [r1])]
        (assert-invalid
          coll
          [:temporal :range-date-times 0]
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])))

    (testing "multiple errors"
      (let [r1 (range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (range-date-time "2000-12-30T19:00:02Z" "2000-12-30T19:00:01Z")
            coll (coll-with-range-date-times [r1 r2])]
        (assert-multiple-invalid
          coll
          [{:path [:temporal :range-date-times 0],
            :errors
            ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
           {:path [:temporal :range-date-times 1],
            :errors
            ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])))))

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
            coll
            [:related-urls]
            [(format "Related Urls must be unique. This contains duplicates named [%s]." url)]))))))

(deftest collection-two-d-coordinate-systems-validation
  (let [t1 (c/map->TwoDCoordinateSystem {:name "T1"
                                         :coordinate-1 (c/map->Coordinate {:min-value 0.0
                                                                           :max-value 6.0})
                                         :coordinate-2 (c/map->Coordinate {:min-value 10.0
                                                                           :max-value 10.0})})
        t2 (c/map->TwoDCoordinateSystem {:name "T2"
                                         :coordinate-1 (c/map->Coordinate {:min-value 0.0})
                                         :coordinate-2 (c/map->Coordinate {:max-value 26.0})})
        t3 (c/map->TwoDCoordinateSystem {:name "T3"
                                         :coordinate-1 (c/map->Coordinate {:min-value 10.0
                                                                           :max-value 6.0})})
        t4 (c/map->TwoDCoordinateSystem {:name "T4"
                                         :coordinate-1 (c/map->Coordinate {:min-value 0.0
                                                                           :max-value 6.0})
                                         :coordinate-2 (c/map->Coordinate {:min-value 50.0
                                                                           :max-value 26.0})})]
    (testing "valid two-d-coordinate-systems"
      (assert-valid (c/map->UmmCollection {:two-d-coordinate-systems [t1 t2]})))

    (testing "invalid two-d-coordinate-systems"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:two-d-coordinate-systems [t1 t1]})]
          (assert-invalid
            coll
            [:two-d-coordinate-systems]
            ["Two D Coordinate Systems must be unique. This contains duplicates named [T1]."])))
      (testing "invalid coordinate"
        (let [coll (c/map->UmmCollection {:two-d-coordinate-systems [t3]})]
          (assert-invalid
            coll
            [:two-d-coordinate-systems 0 :coordinate-1]
            ["Coordinate 1 minimum [10.0] must be less than the maximum [6.0]."])))
      (testing "multiple validation errors"
        (let [coll (c/map->UmmCollection {:two-d-coordinate-systems [t1 t1 t3 t4]})]
          (assert-multiple-invalid
            coll
            [{:path [:two-d-coordinate-systems],
              :errors
              ["Two D Coordinate Systems must be unique. This contains duplicates named [T1]."]}
             {:path [:two-d-coordinate-systems 2 :coordinate-1],
              :errors
              ["Coordinate 1 minimum [10.0] must be less than the maximum [6.0]."]}
             {:path [:two-d-coordinate-systems 3 :coordinate-2],
              :errors
              ["Coordinate 2 minimum [50.0] must be less than the maximum [26.0]."]}]))))))

(deftest collection-associations-validation
  (testing "valid collection associations"
    (assert-valid (c/map->UmmCollection
                    {:collection-associations
                     [(c/map->CollectionAssociation {:short-name "S1"
                                                     :version-id "V1"})
                      (c/map->CollectionAssociation {:short-name "S2"
                                                     :version-id "V1"})
                      (c/map->CollectionAssociation {:short-name "S1"
                                                     :version-id "V2"})]})))

  (testing "invalid collection associations"
    (testing "duplicate names"
      (let [coll (c/map->UmmCollection
                   {:collection-associations
                    [(c/map->CollectionAssociation {:short-name "S1"
                                                    :version-id "V1"})
                     (c/map->CollectionAssociation {:short-name "S2"
                                                    :version-id "V1"})
                     (c/map->CollectionAssociation {:short-name "S1"
                                                    :version-id "V1"})]})]
        (assert-invalid
          coll
          [:collection-associations]
          ["Collection Associations must be unique. This contains duplicates named [(ShortName [S1] & VersionId [V1])]."])))))
