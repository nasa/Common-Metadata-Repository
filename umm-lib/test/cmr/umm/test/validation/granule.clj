(ns cmr.umm.test.validation.granule
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm.validation.core :as v]
            [cmr.umm.collection :as c]
            [cmr.umm.granule :as g]
            [cmr.umm.test.validation.helpers :refer :all]
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

(defn make-collection
  "Creates a valid collection with the given attributes"
  [attribs]
  (merge (c/map->UmmCollection {:entry-title "et"})
         attribs))

(defn make-granule
  "Creates a valid granule with the given attributes"
  [attribs]
  (merge
    (g/map->UmmGranule {:collection-ref (g/map->CollectionRef {:entry-title "et"})})
    attribs))

(defn gran-with-geometries
  [geometries]
  (make-granule {:spatial-coverage (c/map->SpatialCoverage {:geometries geometries})}))

;; This is built on top of the existing spatial validation. It just ensures that the spatial
;; validation is being called
(deftest granule-spatial-coverage
  (let [collection (make-collection {:spatial-coverage {:granule-spatial-representation :geodetic}})
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

(defn granule-with-temporal
  [a b]
  (make-granule {:temporal (g/map->GranuleTemporal {:range-date-time (range-date-time a b)})}))

(deftest granule-temporal-coverage
  (let [coll-start "2015-01-01T00:00:00Z"
        coll-end   "2015-01-02T00:00:00Z"
        coll-range (range-date-time coll-start coll-end)
        coll (merge (coll-with-range-date-times [coll-range]) {:entry-title "et"})
        assert-valid #(assert-valid-gran coll %)
        assert-invalid #(assert-invalid-gran coll %1 [:temporal] [%2])]
    (testing "Granule with no temporal coverage values is valid"
      (assert-valid (make-granule {})))

    (testing "Granule with range equal to collection range is valid"
      (assert-valid (granule-with-temporal coll-start coll-end)))

    (testing "Granule with range inside of collection range is valid"
      (assert-valid (granule-with-temporal "2015-01-01T01:00:00Z" "2015-01-01T02:00:00Z")))

    (assert-invalid (granule-with-temporal "2015-01-01T02:00:00Z" "2015-01-01T01:00:00Z")
                    "Granule start date is later than granule end date.")

    (assert-invalid (granule-with-temporal "2014-01-01T00:00:00Z" "2015-01-02T01:00:00Z")
                    "Granule start date is earlier than collection start date.")

    (assert-invalid (granule-with-temporal "2015-01-01T00:00:00Z" "2015-01-02T01:00:00Z")
                    "Granule end date is later than collection end date.")))

(deftest granule-project-refs
  (let [c1 (c/map->Project {:short-name "C1"})
        c2 (c/map->Project {:short-name "C2"})
        c3 (c/map->Project {:short-name "C3"})
        collection (make-collection {:projects [c1 c2 c3]})]
    (testing "Valid project-refs"
      (assert-valid-gran collection (make-granule {}))
      (assert-valid-gran collection (make-granule {:project-refs ["C1"]}))
      (assert-valid-gran collection (make-granule {:project-refs ["C1" "C2" "C3"]})))
    (testing "Invalid project-refs"
      (assert-invalid-gran
        collection
        (make-granule {:project-refs ["C4"]})
        [:project-refs]
        ["Project References have [C4] which do not reference any projects in parent collection."])
      (assert-invalid-gran
        collection
        (make-granule {:project-refs ["C1" "C2" "C3" "C4" "C5"]})
        [:project-refs]
        ["Project References have [C5, C4] which do not reference any projects in parent collection."]))
    (testing "Invalid project-refs unique name"
      (assert-invalid-gran
        collection
        (make-granule {:project-refs ["C1" "C2" "C3" "C1"]})
        [:project-refs]
        ["Project References must be unique. This contains duplicates named [C1]."]))))

(deftest granule-platform-refs
  (let [s1 (c/map->Sensor {:short-name "S1"
                           :characteristics [(c/map->Characteristic
                                               {:name "C3"})
                                             (c/map->Characteristic
                                               {:name "C4"})]})
        s2 (c/map->Sensor {:short-name "S2"
                           :characteristics [(c/map->Characteristic
                                               {:name "C5"})]})
        s3 (c/map->Sensor {:short-name "S3"})
        i1 (c/map->Instrument {:short-name "I1"
                               :characteristics [(c/map->Characteristic
                                                   {:name "C1"})
                                                 (c/map->Characteristic
                                                   {:name "C2"})]
                               :sensors [s1 s2]
                               :operation-modes ["OM1" "OM2"]})
        i2 (c/map->Instrument {:short-name "I2"
                               :sensors [s1 s2]
                               :operation-modes ["OM3" "OM4"]})
        i3 (c/map->Instrument {:short-name "I3"
                               :sensors [s2 s3]})
        i4 (c/map->Instrument {:short-name "I3"})
        p1 (c/map->Platform {:short-name "p1"
                             :instruments [i1 i2]})
        p2 (c/map->Platform {:short-name "p2"
                             :instruments [i1 i3]})
        p3 (c/map->Platform {:short-name "p3"
                             :instruments [i1]})
        p4 (c/map->Platform {:short-name "P3"})
        sg1 (g/map->SensorRef {:short-name "S1"
                               :characteristic-refs [(g/map->CharacteristicRef {:name "C3"})
                                                     (g/map->CharacteristicRef {:name "C4"})]})
        sg2 (g/map->SensorRef {:short-name "S2"})
        sg3 (g/map->SensorRef {:short-name "S3"})
        ig1 (g/map->InstrumentRef {:short-name "I1"
                                   :characteristic-refs [(g/map->CharacteristicRef {:name "C1"})
                                                         (g/map->CharacteristicRef {:name "C2"})]
                                   :sensor-refs [sg1 sg2]
                                   :operation-modes ["OM1" "OM2"]})
        ig2 (g/map->InstrumentRef {:short-name "I2"
                                   :sensor-refs [sg1 sg2]
                                   :operation-modes ["OM3" "OM4"]})
        ig3 (g/map->InstrumentRef {:short-name "I3" :sensor-refs [sg2 sg3]})
        ig4 (g/map->InstrumentRef {:short-name "I4"})
        ig5 (g/map->InstrumentRef {:short-name "I1"
                                   :sensor-refs [sg1 sg1 sg2]})
        ig6 (g/map->InstrumentRef {:short-name "I3" :sensor-refs [sg2 sg2 sg3 sg3]})
        pg1 (g/map->PlatformRef {:short-name "p1" :instrument-refs [ig1 ig2]})
        pg2 (g/map->PlatformRef {:short-name "p2" :instrument-refs [ig1 ig3]})
        pg3 (g/map->PlatformRef {:short-name "p3" :instrument-refs [ig1]})
        pg4 (g/map->PlatformRef {:short-name "p4" :instrument-refs []})
        pg5 (g/map->PlatformRef {:short-name "p5" :instrument-refs []})
        pg6 (g/map->PlatformRef {:short-name "p2" :instrument-refs [ig5]})
        pg7 (g/map->PlatformRef {:short-name "p2" :instrument-refs [ig1 ig6]})
        collection (make-collection {:platforms [p1 p2 p3 p4]})]
    (testing "Valid platform-refs"
      (assert-valid-gran collection (make-granule {}))
      (assert-valid-gran collection (make-granule {:platform-refs [pg1]}))
      (assert-valid-gran collection (make-granule {:platform-refs [pg1 pg2 pg3]})))
    (testing "Invalid platform-refs"
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg4]})
        [:platform-refs]
        ["The following list of Platform short names did not exist in the referenced parent collection: [p4]."])
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg2 pg3 pg4 pg5]})
        [:platform-refs]
        ["The following list of Platform short names did not exist in the referenced parent collection: [p4, p5]."])
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg1 pg2]})
        [:platform-refs]
        ["Platform References must be unique. This contains duplicates named [p1]."])
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg1 pg2 pg2]})
        [:platform-refs]
        ["Platform References must be unique. This contains duplicates named [p1, p2]."])
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg1 pg3 pg4 pg5]})
        [:platform-refs]
        ["Platform References must be unique. This contains duplicates named [p1]."
         "The following list of Platform short names did not exist in the referenced parent collection: [p4, p5]."]))
    (testing "granule platform references parent"
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg4]})
        [:platform-refs]
        ["The following list of Platform short names did not exist in the referenced parent collection: [p4]."])
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg2 pg3 pg4 pg5]})
        [:platform-refs]
        ["The following list of Platform short names did not exist in the referenced parent collection: [p4, p5]."]))
    (testing "granule unique platform short-names"
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg1 pg2]})
        [:platform-refs]
        ["Platform References must be unique. This contains duplicates named [p1]."])
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg1 pg2 pg2]})
        [:platform-refs]
        ["Platform References must be unique. This contains duplicates named [p1, p2]."]))
    (testing "granule unique instrument short-names"
      (assert-invalid-gran
        collection
        (make-granule
          {:platform-refs [(g/map->PlatformRef {:short-name "p2" :instrument-refs [ig1 ig3 ig1]})]})
        [:platform-refs 0 :instrument-refs]
        ["Instrument References must be unique. This contains duplicates named [I1]."])
      (assert-invalid-gran
        collection
        (make-granule
          {:platform-refs
           [pg1 (g/map->PlatformRef {:short-name "p2" :instrument-refs [ig1 ig1 ig3 ig3]})]})
        [:platform-refs 1 :instrument-refs]
        ["Instrument References must be unique. This contains duplicates named [I1, I3]."]))
    (testing "granule instrument reference parent"
      (assert-invalid-gran
        collection
        (make-granule
          {:platform-refs [(g/map->PlatformRef {:short-name "p2" :instrument-refs [ig1 ig4]})]})
        [:platform-refs 0 :instrument-refs]
        ["The following list of Instrument short names did not exist in the referenced parent collection: [I4]."]))
    (testing "granule instrument characteristic-refs reference parent"
      (assert-invalid-gran
        collection
        (make-granule
          {:platform-refs [(g/map->PlatformRef
                             {:short-name "p2"
                              :instrument-refs [(g/map->InstrumentRef
                                                  {:short-name "I1"
                                                   :characteristic-refs [(g/map->CharacteristicRef
                                                                           {:name "C3"})
                                                                         (g/map->CharacteristicRef
                                                                           {:name "C4"})]})]})]})
        [:platform-refs 0 :instrument-refs 0 :characteristic-refs]
        ["The following list of Characteristic Reference names did not exist in the referenced parent collection: [C3, C4]."]))
    (testing "granule instrument characteristic-refs unique name"
      (assert-invalid-gran
        collection
        (make-granule
          {:platform-refs
           [(g/map->PlatformRef
              {:short-name "p1"
               :instrument-refs [ig2 (g/map->InstrumentRef
                                       {:short-name "I1"
                                        :characteristic-refs [(g/map->CharacteristicRef
                                                                {:name "C1"})
                                                              (g/map->CharacteristicRef
                                                                {:name "C1"})]})]})]})
        [:platform-refs 0 :instrument-refs 1 :characteristic-refs]
        ["Characteristic References must be unique. This contains duplicates named [C1]."]))
    (testing "granule instrument operation modes reference parent"
      (assert-invalid-gran
        collection
        (make-granule
          {:platform-refs [(g/map->PlatformRef
                             {:short-name "p1"
                              :instrument-refs [ig2 (g/map->InstrumentRef
                                                      {:short-name "I1"
                                                       :operation-modes ["OM1" "OM3" "OM4"]})]})]})
        [:platform-refs 0 :instrument-refs 1]
        ["The following list of Instrument operation modes did not exist in the referenced parent collection: [OM3, OM4]."]))
    (testing "granule sensor reference parent"
      (let [iref (g/map->InstrumentRef {:short-name "I1" :sensor-refs [sg1 sg3]})]
        (assert-invalid-gran
          collection
          (make-granule
            {:platform-refs [(g/map->PlatformRef {:short-name "p1" :instrument-refs [ig2 iref]})]})
          [:platform-refs 0 :instrument-refs 1 :sensor-refs]
          ["The following list of Sensor short names did not exist in the referenced parent collection: [S3]."])))
    (testing "granule unique sensor short-names"
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg6]})
        [:platform-refs 0 :instrument-refs 0 :sensor-refs]
        ["Sensor References must be unique. This contains duplicates named [S1]."])
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg7]})
        [:platform-refs 1 :instrument-refs 1 :sensor-refs]
        ["Sensor References must be unique. This contains duplicates named [S2, S3]."]))
    (testing "granule sensor characteristic-refs unique name"
      (let [iref (g/map->InstrumentRef
                   {:short-name "I1"
                    :sensor-refs [sg1 (g/map->SensorRef
                                        {:short-name "S2"
                                         :characteristic-refs [(g/map->CharacteristicRef
                                                                 {:name "C5" :value "V1"})
                                                               (g/map->CharacteristicRef
                                                                 {:name "C5" :value "V2"})]})]})]
        (assert-invalid-gran
          collection
          (make-granule
            {:platform-refs [(g/map->PlatformRef {:short-name "p1" :instrument-refs [ig2 iref]})]})
          [:platform-refs 0 :instrument-refs 1 :sensor-refs 1 :characteristic-refs]
          ["Characteristic References must be unique. This contains duplicates named [C5]."])))
    (testing "granule sensor characteristic-refs reference parent"
      (let [iref (g/map->InstrumentRef
                   {:short-name "I1"
                    :sensor-refs [(g/map->SensorRef
                                    {:short-name "S1"
                                     :characteristic-refs [(g/map->CharacteristicRef
                                                             {:name "C1" :value "V1"})
                                                           (g/map->CharacteristicRef
                                                             {:name "C2" :value "V2"})]})]})]
        (assert-invalid-gran
          collection
          (make-granule
            {:platform-refs [(g/map->PlatformRef {:short-name "p1" :instrument-refs [ig2 iref]})]})
          [:platform-refs 0 :instrument-refs 1 :sensor-refs 0 :characteristic-refs]
          ["The following list of Characteristic Reference names did not exist in the referenced parent collection: [C1, C2]."])))
    (testing "multiple granule platform validation errors"
      (assert-invalid-gran
        collection
        (make-granule {:platform-refs [pg1 pg1 pg3 pg4 pg5]})
        [:platform-refs]
        ["Platform References must be unique. This contains duplicates named [p1]."
         "The following list of Platform short names did not exist in the referenced parent collection: [p4, p5]."]))))

(deftest granule-product-specific-attributes
  (let [p1 (c/map->ProductSpecificAttribute {:name "AA1"
                                             :description "something string"
                                             :data-type :string
                                             :parameter-range-begin "alpha"
                                             :parameter-range-end "bravo"
                                             :value "alpha1"})
        p2 (c/map->ProductSpecificAttribute {:name "AA2"
                                             :description "something float"
                                             :data-type :float
                                             :parameter-range-begin 0.1
                                             :parameter-range-end 100.43})
        pg1 (g/map->ProductSpecificAttributeRef {:name "AA1"
                                                 :values ["alpha" "alpha1"]})
        pg2 (g/map->ProductSpecificAttributeRef {:name "AA2"
                                                 :values [12.3 15.0]})
        pg3 (g/map->ProductSpecificAttributeRef {:name "AA3"
                                                 :values ["alpha" "alpha1"]})
        pg4 (g/map->ProductSpecificAttributeRef {:name "AA4"
                                                 :values [1.0]})
        collection (make-collection {:product-specific-attributes [p1 p2]})]
    (testing "Valid product-specific-attributes"
      (assert-valid-gran collection (make-granule {}))
      (assert-valid-gran collection (make-granule {:product-specific-attributes [pg1]}))
      (assert-valid-gran collection (make-granule {:product-specific-attributes [pg1 pg2]})))
    (testing "Invalid product-specific-attributes"
      (assert-invalid-gran
        collection
        (make-granule {:product-specific-attributes [pg3]})
        [:product-specific-attributes]
        ["The following list of Product Specific Attributes did not exist in the referenced parent collection: [AA3]."])
      (assert-invalid-gran
        collection
        (make-granule {:product-specific-attributes [pg1 pg2 pg3 pg4]})
        [:product-specific-attributes]
        ["The following list of Product Specific Attributes did not exist in the referenced parent collection: [AA3, AA4]."]))))

(deftest granule-online-access-urls-validation
  (let [url "http://example.com/url2"
        r1 (c/map->RelatedURL {:type "GET DATA"
                               :url "http://example.com/url1"})
        r2 (c/map->RelatedURL {:type "GET DATA"
                               :url url})
        r3 (c/map->RelatedURL {:type "GET RELATED VISUALIZATION"
                               :url url})
        collection (make-collection {})]
    (testing "valid online access urls"
      (assert-valid-gran collection (make-granule {:related-urls [r1 r2 r3]})))

    (testing "invalid online access urls with duplicate names"
      (assert-invalid-gran
        collection
        (make-granule {:related-urls [r1 r2 r2]})
        [:related-urls]
        [(format "Related Urls must be unique. This contains duplicates named [%s]." url)]))))
